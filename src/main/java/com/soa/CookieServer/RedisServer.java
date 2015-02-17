package com.soa.CookieServer;

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

import com.twitter.finagle.Http;
import com.twitter.finagle.ListeningServer;
import com.twitter.finagle.Service;
import com.twitter.util.Await;
import com.twitter.util.Future;
import com.twitter.util.TimeoutException;

class CacheService extends Service<HttpRequest, HttpResponse> {
    private static JSONParser jsonParser = new JSONParser();
    private RedisCache redisCache = new RedisCache("localhost", 7000);

    @Override
    public Future<HttpResponse> apply(HttpRequest request) {
        String reqContent = request.getContent().toString(CharsetUtil.UTF_8);
        System.out.println("[Helper] Request received: " + reqContent);

        // Parsing JSON request
        JSONObject jreq;
        try {
            jreq = (JSONObject) jsonParser.parse(reqContent);
            System.out.println("[RedisServer] Param received - pname:" + jreq.get("pname"));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.setContent(ChannelBuffers.copiedBuffer("{\"v_id\":100, \"price\":0.2}", CharsetUtil.UTF_8));
        res.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
        return Future.<HttpResponse> value(res);
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
