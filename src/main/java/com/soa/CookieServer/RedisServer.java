package com.soa.CookieServer;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.InetSocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.twitter.finagle.Http;
import com.twitter.finagle.ListeningServer;
import com.twitter.finagle.Service;
import com.twitter.util.Await;
import com.twitter.util.Function;
import com.twitter.util.Future;
import com.twitter.util.TimeoutException;

class CacheService extends Service<HttpRequest, HttpResponse> {
    private static JSONParser jsonParser = new JSONParser();
    private RedisCache redisCache = new RedisCache("localhost", 7000);

    @Override
    public Future<HttpResponse> apply(HttpRequest request) {
        String reqContent = request.getContent().toString(CharsetUtil.UTF_8);
        System.out.println("[Redis Server] Request received: " + reqContent);

        // Parsing JSON request from CookieServer
        JSONObject jreq = null;
        try {
            jreq = (JSONObject) jsonParser.parse(reqContent);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        
        Future<HttpResponse> future = null;
        
        // Run the redis command and process response to CookieServer
        String command = (String) jreq.get("command");
        String key = (String) jreq.get("key");
        if (command.equals("SMEMBERS")) {
            Future<scala.collection.immutable.Set<ChannelBuffer>> udResponse = redisCache.sMembers(key);
            
            future = udResponse.flatMap(new Function<scala.collection.immutable.Set<ChannelBuffer>, Future<HttpResponse>> () {
                @SuppressWarnings("unchecked")
                public Future<HttpResponse> apply(scala.collection.immutable.Set<ChannelBuffer> udBuf) {
                    HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    JSONObject jsonReq = new JSONObject();
                    jsonReq.put("status", "SUCCESS");
                    JSONArray optOutArr = new JSONArray();
                    for (ChannelBuffer cb : scala.collection.JavaConversions.asJavaIterable(udBuf)) {
                        optOutArr.add(cb.toString(UTF_8));
                    }
                    jsonReq.put("value", optOutArr);
                    ChannelBuffer buffer = ChannelBuffers.copiedBuffer(jsonReq.toJSONString(), UTF_8);
                    res.setContent(buffer);
                    res.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
                    res.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buffer.readableBytes());
                    return Future.value(res);
                }
            });
        } 
        else if (command.equals("SADD")) {
            String value = (String) jreq.get("value");
            Long ttl = (Long) jreq.get("ttl");
            Future<Long> length = redisCache.sAdd(key, value, ttl);
            
            future = length.flatMap(new Function<Long, Future<HttpResponse>> () {
                @SuppressWarnings("unchecked")
                public Future<HttpResponse> apply(Long length) {
                    HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    JSONObject jsonReq = new JSONObject();
                    jsonReq.put("status", "SUCCESS");
                    ChannelBuffer buffer = ChannelBuffers.copiedBuffer(jsonReq.toJSONString(), UTF_8);
                    res.setContent(buffer);
                    res.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
                    res.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buffer.readableBytes());
                    return Future.value(res);
                }
            });
        } 
        else if (command.equals("SREM")) {
            String value = (String) jreq.get("value");
            Future<Long> length = redisCache.sRem(key, value);
            
            future = length.flatMap(new Function<Long, Future<HttpResponse>> () {
                @SuppressWarnings("unchecked")
                public Future<HttpResponse> apply(Long length) {
                    HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    JSONObject jsonReq = new JSONObject();
                    jsonReq.put("status", "SUCCESS");
                    ChannelBuffer buffer = ChannelBuffers.copiedBuffer(jsonReq.toJSONString(), UTF_8);
                    res.setContent(buffer);
                    res.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
                    res.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buffer.readableBytes());
                    return Future.value(res);
                }
            });
        }
        
        return future;
    }

}

public class RedisServer {

    private void start() {
        ListeningServer server = Http.serve(new InetSocketAddress("localhost", 8002), new CacheService());

        System.out.println("[RedisServer] Starting..");
        try {
            Await.ready(server);
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        RedisServer redisServer = new RedisServer();
        redisServer.start();
    }

}
