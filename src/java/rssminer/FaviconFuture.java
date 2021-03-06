/*
 * Copyright (c) Feng Shen<shenedu@gmail.com>. All rights reserved.
 * You must not remove this notice, or any other, from this software.
 */

package rssminer;

import static me.shenfeng.http.HttpUtils.ACCEPT_ENCODING;
import static me.shenfeng.http.HttpUtils.CACHE_CONTROL;
import static me.shenfeng.http.HttpUtils.CONTENT_TYPE;
import static me.shenfeng.http.HttpUtils.LOCATION;
import static rssminer.Utils.CLIENT;

import java.io.ByteArrayInputStream;
import java.net.Proxy;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import me.shenfeng.http.DynamicBytes;
import me.shenfeng.http.client.BinaryRespListener;
import me.shenfeng.http.client.IBinaryHandler;
import me.shenfeng.http.client.ITextHandler;
import me.shenfeng.http.client.TextRespListener;
import me.shenfeng.http.server.ClojureRing;
import me.shenfeng.http.server.IListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rssminer.jsoup.HtmlUtils;
import clojure.lang.Keyword;

public class FaviconFuture implements IListenableFuture {

    static Logger logger = LoggerFactory.getLogger(FaviconFuture.class);

    static final int MAX_RETRY = 5;

    private volatile Runnable listener;
    private volatile Object resp;
    protected volatile int retryCount = 0;

    private final String hostname;
    protected final Proxy proxy;
    protected final DataSource dataSource;
    protected final Map<String, String> reqHeaders;

    static final int CACHE_TIME = 3600 * 24 * 5; // cache 5 day;
    static final String CACHE_VALUE = "public, max-age=" + CACHE_TIME;

    public Object get() {
        return resp;
    }

    private void noIcon() {
        finish(404, null, true); // cache failed
    }

    protected Map<String, String> getHeaders(String ct) {
        Map<String, String> h = new TreeMap<String, String>();
        if (ct != null && !ct.isEmpty()) {
            h.put(CONTENT_TYPE, ct);
        }
        // rand value, wait for If-Modified-Since
        h.put("Last-Modified", "Sun, 09 Dec 2012 09:27:29 +0000");
        h.put(CACHE_CONTROL, CACHE_VALUE);
        return h;
    }

    private void finish(int status, byte[] data, boolean cache) {
        if (cache) {
            Connection con = null;
            PreparedStatement ps = null;
            try {
                con = dataSource.getConnection();
                ps = con.prepareStatement("insert into favicon(favicon, code, hostname) values (?, ?, ?)");
                ps.setBytes(1, data);
                ps.setInt(2, status);
                ps.setString(3, hostname);
                ps.executeUpdate();
            } catch (SQLException ignore) {
            } finally {
                Utils.closeQuietly(con);
                Utils.closeQuietly(ps);
            }
        }
        if (data != null) {
            done(200, getHeaders("image/x-icon"), new ByteArrayInputStream(data));
        } else {
            // browser js will do it right
            done(200, getHeaders(null), null);
        }
    }

    protected void done(int status, Map<String, String> headers, Object body) {
        Map<Keyword, Object> r = new HashMap<Keyword, Object>(6);
        r.put(ClojureRing.STATUS, status);
        r.put(ClojureRing.HEADERS, headers);
        r.put(ClojureRing.BODY, body);
        resp = r;
        if (listener != null) {
            listener.run(); // will call get, get resp
        }
    }

    private class FaviconHandler implements IBinaryHandler {
        private final URI base;

        public FaviconHandler(URI u) {
            this.base = u;
        }

        public void onSuccess(int status, Map<String, String> headers, DynamicBytes bytes) {
            if (status == 301 || status == 302) {
                String loc = headers.get(LOCATION);
                if (loc != null) {
                    doIt(base.resolve(loc), true);
                } else {
                    noIcon();
                }
            } else {
                byte[] data = Arrays.copyOf(bytes.get(), bytes.length());
                finish(status, data, true);
            }
        }

        public void onThrowable(Throwable t) {
            noIcon();
        }
    }

    private class WebPageHandler implements ITextHandler {

        private final URI base;

        public WebPageHandler(URI u) {
            this.base = u;
        }

        public void onSuccess(int status, Map<String, String> headers, String body) {
            if (status == 301 || status == 302) {
                String loc = headers.get(LOCATION);
                if (loc != null) {
                    doIt(base.resolve(loc), false);
                } else {
                    noIcon();
                }
            } else if (status == 200) {
                try {
                    URI uri = HtmlUtils.extractFavicon(body, base);
                    if (uri == null) {
                        uri = new URI(base.getScheme() + "://" + base.getHost()
                                + "/favicon.ico");
                    }
                    doIt(uri, true);
                } catch (Exception e) {
                    noIcon();
                }
            }
        }

        public void onThrowable(Throwable t) {
            noIcon();
        }
    }

    public void addListener(Runnable listener) {
        if (this.listener != null)
            throw new RuntimeException("listener is already added");
        if (resp != null) {
            listener.run(); // listener will call get(), and get the real resp
        } else {
            this.listener = listener;
        }
    }

    private void doIt(URI u, boolean img) {
        if (++retryCount > MAX_RETRY) {
            noIcon();
            return;
        }
        try {
            if (img) {
                Map<String, String> headers = new TreeMap<String, String>(reqHeaders);
                headers.put(ACCEPT_ENCODING, null);
                CLIENT.get(u, headers, proxy, new BinaryRespListener(new FaviconHandler(u)));
            } else {
                CLIENT.get(u, reqHeaders, proxy, new TextRespListener(new WebPageHandler(u)));
            }
        } catch (Exception e) {
            noIcon();
        }
    }

    private boolean tryCache() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            con = dataSource.getConnection();
            ps = con.prepareStatement("SELECT favicon, code FROM favicon WHERE hostname = ?");
            ps.setString(1, hostname);
            rs = ps.executeQuery();
            if (rs.next()) {
                int code = rs.getInt(2);
                if (code == 200) {
                    byte[] data = rs.getBytes(1);
                    done(200, getHeaders("image/x-icon"), new ByteArrayInputStream(data));
                } else {
                    noIcon();
                }
                return true;
            }
        } catch (SQLException ignore) {
        } finally {
            Utils.closeQuietly(rs);
            Utils.closeQuietly(ps);
            Utils.closeQuietly(con);
        }
        return false;
    }

    public FaviconFuture(String hostname, Map<String, String> headers, Proxy proxy,
            DataSource dataSource) {
        this.proxy = proxy;
        this.dataSource = dataSource;
        this.reqHeaders = headers;
        this.hostname = hostname;

        if (proxy == null || dataSource == null) {
            throw new NullPointerException("proxy, redis, datasource can's be null");
        }
        if (!tryCache()) {
            try {
                doIt(new URI("http://" + hostname + "/"), false);
            } catch (Exception e) {
                noIcon();
            }
        }
    }
}
