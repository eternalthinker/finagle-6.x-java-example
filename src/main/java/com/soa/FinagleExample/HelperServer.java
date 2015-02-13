package com.soa.FinagleExample;

import java.net.InetSocketAddress;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
/*import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;*/

import com.twitter.finagle.Service;
import com.twitter.finagle.builder.ServerBuilder;
import com.twitter.finagle.http.Http;
import com.twitter.util.Future;

public class HelperServer {
    static JSONParser jsonParser = new JSONParser();
    
    public static void main(String[] args) {
        Service<HttpRequest, HttpResponse> service = new Service<HttpRequest, HttpResponse>() {
            @Override
            public Future<HttpResponse> apply(HttpRequest request) {
                String reqContent = request.getContent().toString(CharsetUtil.UTF_8);
                System.out.println("[Helper] Request received: " + reqContent);
                
                // Parsing JSON request
                JSONObject jreq;
                try {
                    jreq = (JSONObject) jsonParser.parse(reqContent);
                    System.out.println("[Main] Param received - pname:" + jreq.get("pname"));
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                
                HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                res.setContent(ChannelBuffers.copiedBuffer("{\"v_id\":100, \"price\":0.2}", CharsetUtil.UTF_8));
                res.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
                return Future.<HttpResponse> value(res);
            }
        };
        
        ServerBuilder.safeBuild(service,
                ServerBuilder.get().codec(Http.get()).name("HttpServer")
                .bindTo(new InetSocketAddress("localhost", 9004)));
    }
}


