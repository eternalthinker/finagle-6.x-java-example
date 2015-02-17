package com.soa.CookieServer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultCookie;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.util.CharsetUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.twitter.finagle.Http;
import com.twitter.finagle.ListeningServer;
import com.twitter.finagle.Service;
import com.twitter.finagle.http.HttpMuxer;
import com.twitter.util.Await;
import com.twitter.util.Function;
import com.twitter.util.Future;
import com.twitter.util.TimeoutException;

class AnalyzeService2 extends Service<HttpRequest, HttpResponse> {
    private static JSONParser jsonParser = new JSONParser();
    private final String COOKIE_NAME = "vizid";

    @SuppressWarnings("unchecked")
    public Future<HttpResponse> apply(HttpRequest req) {
        // Parse URL parameters
        String bannerSize = null;
        String opt = null;
        QueryStringDecoder decoder = new QueryStringDecoder(req.getUri());
        Map <String, List<String>> params = decoder.getParameters();
        if (params.containsKey("bsize")) {
            bannerSize = params.get("bsize").get(0);
            opt = params.get("opt").get(0);
        }

        // Parse cookie
        Cookie cookie = null;
        String value = req.getHeader("Cookie");
        if (value != null) {
            Set<Cookie> cookies = new CookieDecoder().decode(value);
            for (Cookie c : cookies) {
                if (c.getName().equals(COOKIE_NAME)) {
                    cookie = c;
                }
            }
        }
        String vizid = null;
        if (cookie != null) {
            vizid = cookie.getValue();
        } else {
            vizid = UUID.randomUUID().toString();
            // TODO Avoid cache access
        }

        // Get data from redis cache, through RedisServer
        Service<HttpRequest, HttpResponse> client = Http.newService(":8002");
        HttpRequest helperRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        JSONObject jsonReq = new JSONObject();
        jsonReq.put("key", vizid);
        jsonReq.put("command", "SMEMBERS");
        ChannelBuffer buffer = ChannelBuffers.copiedBuffer(jsonReq.toJSONString(), UTF_8);
        helperRequest.setContent(buffer);
        helperRequest.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
        helperRequest.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buffer.readableBytes());
        Future<HttpResponse> helperResponse = client.apply(helperRequest);
        client.close();

        Future<HttpResponse> response = helperResponse.flatMap(new ProcessResponse2(req, vizid, 
                bannerSize, opt, jsonParser));
        System.out.println("[CookieServer] Returning response Future");
        return response;
    } 
}


class ProcessResponse2 extends Function<HttpResponse, Future<HttpResponse>> {
    private HttpRequest request;
    private String vizid;
    private String bannerSize;
    private String opt;
    private String[] sizes = {"200x200",
            "300x200",
            "700x150",
            "150x400"};
    private JSONParser jsonParser;

    public ProcessResponse2(HttpRequest req, String vizid, String bannerSize, String opt, JSONParser jsonParser) {
        this.request = req;
        this.vizid = vizid;
        this.bannerSize = bannerSize;
        this.opt = opt;
        this.jsonParser = jsonParser;
    }

    @SuppressWarnings("unchecked")
    public Future<HttpResponse> apply(HttpResponse res) {
        // Parse res
        String resContent = res.getContent().toString(CharsetUtil.UTF_8);
        System.out.println("[Cookie Server] Response received from cache: " + resContent);
        JSONArray jres = null;
        try {
            JSONObject resObj = (JSONObject) jsonParser.parse(resContent);
            jres = (JSONArray) resObj.get("value");
        } catch (ParseException e) {
            e.printStackTrace();
        }
        
        Set<String> sizeOptOuts = new TreeSet<String>();
        for (Object sizeObj : jres) {
            sizeOptOuts.add((String)sizeObj);
        }
        
        // Asynchronously update cache, through RedisServer
        if (bannerSize != null) {
            if (!sizeOptOuts.contains(bannerSize) && opt.equals("F")) {
                sizeOptOuts.add(bannerSize);
                Service<HttpRequest, HttpResponse> client = Http.newService("localhost:8002");
                HttpRequest helperRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
                JSONObject jsonReq = new JSONObject();
                jsonReq.put("key", vizid);
                jsonReq.put("command", "SADD");
                jsonReq.put("value", bannerSize);
                jsonReq.put("ttl", new Long(60*10));
                ChannelBuffer buffer = ChannelBuffers.copiedBuffer(jsonReq.toJSONString(), UTF_8);
                helperRequest.setContent(buffer);
                helperRequest.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
                helperRequest.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buffer.readableBytes());
                @SuppressWarnings("unused")
                Future<HttpResponse> helperResponse = client.apply(helperRequest);
                client.close();
            } else if (sizeOptOuts.contains(bannerSize) && opt.equals("T")) {
                sizeOptOuts.remove(bannerSize);
                Service<HttpRequest, HttpResponse> client = Http.newService("localhost:8002");
                HttpRequest helperRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
                JSONObject jsonReq = new JSONObject();
                jsonReq.put("key", vizid);
                jsonReq.put("command", "SREM");
                jsonReq.put("value", bannerSize);
                ChannelBuffer buffer = ChannelBuffers.copiedBuffer(jsonReq.toJSONString(), UTF_8);
                helperRequest.setContent(buffer);
                helperRequest.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
                helperRequest.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buffer.readableBytes());
                @SuppressWarnings("unused")
                Future<HttpResponse> helperResponse = client.apply(helperRequest);
                client.close();
            }
        }

        // Build response content
        StringBuilder content = new StringBuilder();
        content.append("<!DOCTYPE html>\n<html><body>");
        content.append("ID: " + vizid + "<br><br>");
        for (String size : sizes) {
            content.append("<form method='get' action='analyze.php'> <fieldset>");
            content.append("<input type='hidden' name='bsize' value='" + size + "'>");
            content.append("Banner size: ");
            content.append(size + "&nbsp; &nbsp;");
            boolean optedOut = sizeOptOuts.contains(size);
            content.append("<input type='radio' name='opt' value='T' id='opt-yes'");
            if (! optedOut) {
                content.append(" checked='checked'");
            }
            content.append("/> <label for='opt-yes'>Yes</label>"); 
            content.append("<input type='radio' name='opt' value='F' id='opt-no'");
            if (optedOut) {
                content.append(" checked='checked'");
            }
            content.append("/> <label for='opt-no'>No</label>"); 
            content.append("&nbsp;&nbsp; <button type='submit'>Update</button>");
            content.append("</fieldset></form>");
        }
        content.append("</body></html>");
        // End of response content

        HttpResponse response = new DefaultHttpResponse(
                request.getProtocolVersion(),
                HttpResponseStatus.OK
                );
        response.setContent(copiedBuffer(content.toString(), UTF_8));

        // Build response cookie
        String cookieContent = vizid;
        Cookie resCookie = new DefaultCookie("vizid", cookieContent);
        resCookie.setPath("/");
        resCookie.setMaxAge(60*10);
        CookieEncoder encoder = new CookieEncoder(true);
        encoder.addCookie(resCookie);
        response.setHeader("Set-Cookie", encoder.encode());

        return Future.value(response);
    }
}


public class CookieServer {

    private void start() {
        HttpMuxer muxService = new HttpMuxer().withHandler("/analyze.php", new AnalyzeService2());
        ListeningServer server = Http.serve(new InetSocketAddress(8000), muxService);

        System.out.println("[CookieServer] Starting..");
        try {
            Await.ready(server);
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        CookieServer httpServer = new CookieServer();
        httpServer.start();
    }

}
