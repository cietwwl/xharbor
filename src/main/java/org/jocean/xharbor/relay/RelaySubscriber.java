/**
 * 
 */
package org.jocean.xharbor.relay;

import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jocean.http.server.CachedRequest;
import org.jocean.http.server.HttpServer.HttpTrade;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.xharbor.api.Dispatcher;
import org.jocean.xharbor.api.Router;
import org.jocean.xharbor.api.RoutingInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observers.SerializedSubscriber;

/**
 * @author isdom
 *
 */
public class RelaySubscriber extends Subscriber<HttpTrade> {

    private static class RouterCtxImpl implements Router.Context {
        private final HashMap<String, Object> _map = new HashMap<String, Object>();
        
        @Override
        public <V> RouterCtxImpl setProperty(final String key, final V obj) {
            _map.put(key, obj);
            return this;
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public <V> V getProperty(String key) {
            return (V)_map.get(key);
        }
        
        @Override
        public Map<String, Object> getProperties() {
            return _map;
        }
        
        public void clear() {
            _map.clear();
        }
    }
    
    private static final Logger LOG =
            LoggerFactory.getLogger(RelaySubscriber.class);

    public RelaySubscriber(final Router<HttpRequest, Dispatcher> router) {
        this._router = router;
    }
    
    @Override
    public void onCompleted() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onError(final Throwable e) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onNext(final HttpTrade trade) {
        trade.request().subscribe(
            new SerializedSubscriber<HttpObject>(
                new RequestSubscriber(trade)));
    }
    
    private static Observable<HttpObject> buildHttpResponse(
            final Dispatcher dispatcher, 
            final HttpRequest request,
            final Observable<HttpObject> fullRequest,
            final RoutingInfo info,
            final AtomicBoolean canRetry) {
        return dispatcher.response(info, request, fullRequest)
            .onErrorResumeNext(new Func1<Throwable, Observable<HttpObject>>() {
                @Override
                public Observable<HttpObject> call(final Throwable e) {
                    if (canRetry.get()) {
                        return buildHttpResponse(dispatcher, request, fullRequest, info, canRetry);
                    } else {
                        return Observable.error(e);
                    }
                }})
            .doOnNext(new Action1<HttpObject>() {
                @Override
                public void call(final HttpObject httpObj) {
                    canRetry.set(false);
                }});
    }
    
    class RequestSubscriber extends Subscriber<HttpObject> {
        private final HttpTrade _trade;
        private final CachedRequest _cached;
      
        RequestSubscriber(final HttpTrade trade) {
            this._trade = trade;
            this._cached = new CachedRequest(trade);
        }
        
        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(final Throwable e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("trade({}).request().onError ({}).", 
                    this._trade, ExceptionUtils.exception2detail(e));
            }
            this._cached.destroy();
        }
        
        @Override
        public void onNext(final HttpObject msg) {
            if (msg instanceof HttpRequest) {
                final HttpRequest req = (HttpRequest)msg;
                final RouterCtxImpl routectx = new RouterCtxImpl();
                
                final Dispatcher dispatcher = _router.calculateRoute(req, routectx);
                final RoutingInfo info = routectx.getProperty("routingInfo");
                routectx.clear();
                
                buildHttpResponse(dispatcher, req, _cached.request(), info, new AtomicBoolean(true))
                    .doOnTerminate(new Action0() {
                        @Override
                        public void call() {
                            _cached.destroy();
                        }})
                    .subscribe(_trade.responseObserver());
            }
        }
    };
    
    private final Router<HttpRequest, Dispatcher> _router;
}
