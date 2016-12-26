package org.jocean.xharbor.reactor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jocean.http.server.HttpServerBuilder.HttpTrade;
import org.jocean.http.util.RxNettys;
import org.jocean.idiom.Regexs;
import org.jocean.xharbor.api.TradeReactor;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import rx.Observable;
import rx.Single;
import rx.functions.Func1;

public class RewriteRequestPath implements TradeReactor {

    public RewriteRequestPath(
            final String pathPattern, 
            final String replaceTo) {
        this._pathPattern = Regexs.safeCompilePattern(pathPattern);
        this._replaceTo = replaceTo;
    }
    
    @Override
    public Single<? extends InOut> react(final HttpTrade trade, final InOut io) {
        if (null != io.outbound()) {
            return Single.<InOut>just(null);
        }
        return io.inbound().compose(RxNettys.asHttpRequest())
            .map(new Func1<HttpRequest, InOut>() {
                @Override
                public InOut call(final HttpRequest req) {
                    if (null == req) {
                        return null;
                    } else {
                        final Matcher matcher = _pathPattern.matcher(req.uri());
                        if ( matcher.find() ) {
                            return io4rewritePath(io, req, matcher);
                        } else {
                            //  not handle this trade
                            return null;
                        }
                    }
                }})
            .toSingle();
    }

    private HttpRequest rewriteRequest(
            final Matcher matcher,
            final HttpRequest req) {
        final DefaultHttpRequest newreq = new DefaultHttpRequest(req.protocolVersion(), 
                req.method(), req.uri(), true);
        newreq.headers().set(req.headers());
        return newreq.setUri(matcher.replaceFirst(_replaceTo));
    }

    private InOut io4rewritePath(final InOut originalio, 
            final HttpRequest originalreq,
            final Matcher matcher) {
        return new InOut() {
            @Override
            public Observable<? extends HttpObject> inbound() {
                return Observable.<HttpObject>just(rewriteRequest(matcher, originalreq))
                    .concatWith(
                        originalio.inbound()
                        .flatMap(RxNettys.splitFullHttpMessage())
                        .skip(1));
            }
            @Override
            public Observable<? extends HttpObject> outbound() {
                return originalio.outbound();
            }};
    }

    private final Pattern _pathPattern;
    private final String _replaceTo;
}