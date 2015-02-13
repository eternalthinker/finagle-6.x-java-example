package com.soa.FinagleExample;

import java.net.InetSocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.twitter.finagle.Service;
import com.twitter.finagle.builder.ClientBuilder;
import com.twitter.finagle.builder.ServerBuilder;
import com.twitter.finagle.http.Http;
import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;
import com.twitter.util.Function;

public class MainServer {
    static JSONParser jsonParser = new JSONParser();
    
    static class GetBid extends Function<HttpResponse, Future<HttpResponse>> {
        public Future<HttpResponse> apply(HttpResponse res) {
            String resContent = res.getContent().toString(CharsetUtil.UTF_8);
            System.out.println("[Main] Received helper response: " + resContent);
            
            System.out.println("[Main] Sending response to main client");
            HttpResponse finalRes = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            finalRes.setContent(ChannelBuffers.copiedBuffer("{\"bid\":" + resContent + "}", CharsetUtil.UTF_8));
            finalRes.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
            return Future.<HttpResponse> value(finalRes);
        }
    }
    
    public static void main(String[] args) {
        
        Service<HttpRequest, HttpResponse> service = new Service<HttpRequest, HttpResponse>() {
            @Override
            public Future<HttpResponse> apply(HttpRequest request) {
                String reqContent = request.getContent().toString(CharsetUtil.UTF_8);
                System.out.println("[Main] Request received: " + reqContent);
                
                // Parsing JSON request
                JSONObject mainReq;
                try {
                    mainReq = (JSONObject) jsonParser.parse(reqContent);
                    System.out.println("[Main] Param received - pname:" + mainReq.get("pname"));
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                
                // Initiate request to Helper
                Service<HttpRequest, HttpResponse> client = ClientBuilder
                        .safeBuild(ClientBuilder.get().codec(Http.get())
                                .hosts("localhost:9004").hostConnectionLimit(1));
                
                // TODO The request content is empty with below code; Need to fix this
                HttpRequest helperRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
                ChannelBuffer buffer;
                buffer = request.getContent();
                //buffer = ChannelBuffers.copiedBuffer("{\"dummy\":1}", CharsetUtil.UTF_8);
                helperRequest.setContent(buffer);
                helperRequest.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
                helperRequest.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buffer.readableBytes());
                
                Future<HttpResponse> helperResponse = client.apply(helperRequest);
                client.close();
                
                // TODO Understand how Future exceptions are handled. Eg: Connection Error
                /* helperResponse.addEventListener( 
                        new FutureEventListener<HttpResponse>() {
                            public void onSuccess(HttpResponse helperResponse) {
                                System.out.print("[Main] Received helper response: ");
                                System.out.println(helperResponse.getContent().toString(CharsetUtil.UTF_8));
                            }
                            public void onFailure(Throwable cause) {
                                System.out.println("[Main] Helper failed with cause: " + cause);
                            }
                        }); */
                
                Future <HttpResponse> mainResponse = helperResponse.flatMap(new GetBid());
                return mainResponse;
                
                // return Future.<HttpResponse> value(res);
            }
        };
        
        ServerBuilder.safeBuild(service,
                ServerBuilder.get().codec(Http.get()).name("HttpServer")
                .bindTo(new InetSocketAddress("localhost", 9001)));
    }
}


