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
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultCookie;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;

import com.twitter.finagle.Http;
import com.twitter.finagle.ListeningServer;
import com.twitter.finagle.Service;
import com.twitter.finagle.http.HttpMuxer;
import com.twitter.util.Await;
import com.twitter.util.Function;
import com.twitter.util.Future;
import com.twitter.util.TimeoutException;

class AnalyzeService extends Service<HttpRequest, HttpResponse> {
    private RedisCache redisCache = new RedisCache("localhost", 7000);
    private final String COOKIE_NAME = "vizid";

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
            // TODO Skip cache access
        }

        // Get data from redis cache
        Future<scala.collection.immutable.Set<ChannelBuffer>> udResponse = redisCache.sMembers(vizid);

        Future<HttpResponse> response = udResponse.flatMap(new ProcessResponse(req, vizid, 
                bannerSize, opt, redisCache));
        System.out.println("[CookieServer] Returning response Future");
        return response;
    } 
}


class ProcessResponse extends Function<scala.collection.immutable.Set<ChannelBuffer>, Future<HttpResponse>> {
    private HttpRequest request;
    private String vizid;
    private String bannerSize;
    private String opt;
    private String[] sizes = {"200x200",
            "300x200",
            "700x150",
            "150x400"};
    private RedisCache redisCache;

    public ProcessResponse(HttpRequest req, String vizid, String bannerSize, String opt, RedisCache redisCache) {
        this.request = req;
        this.vizid = vizid;
        this.bannerSize = bannerSize;
        this.opt = opt;
        this.redisCache = redisCache;
    }

    public Future<HttpResponse> apply(scala.collection.immutable.Set<ChannelBuffer> udBuf) {
        // Parse UD
        Set<ChannelBuffer> udBufSet = scala.collection.JavaConversions.setAsJavaSet(udBuf);
        Set<String> sizeOptOuts = new TreeSet<String>();
        for (ChannelBuffer cb : udBufSet) {
            sizeOptOuts.add(cb.toString(UTF_8));
        }

        // Asynchronously update cache
        if (bannerSize != null) {
            if (!sizeOptOuts.contains(bannerSize) && opt.equals("F")) {
                Future<Long> length = redisCache.sAdd(vizid, bannerSize, 60*10);
                sizeOptOuts.add(bannerSize);
            } else if (sizeOptOuts.contains(bannerSize) && opt.equals("T")) {
                Future<Long> length = redisCache.sRem(vizid, bannerSize);
                sizeOptOuts.remove(bannerSize);
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


public class CookieServerSingle {

    private void start() {
        HttpMuxer muxService = new HttpMuxer().withHandler("/analyze.php", new AnalyzeService());
        ListeningServer server = Http.serve(new InetSocketAddress("localhost", 8000), muxService);

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
        CookieServerSingle httpServer = new CookieServerSingle();
        httpServer.start();
    }

}
