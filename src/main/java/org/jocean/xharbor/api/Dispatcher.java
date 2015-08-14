/**
 * 
 */
package org.jocean.xharbor.api;

import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import rx.Observable;

/**
 * @author isdom
 *
 */
public interface Dispatcher {
    
    public Target dispatch();
    
    public boolean IsValid();
    
    public Observable<HttpObject> response(
            final RoutingInfo info,
            final HttpRequest request, 
            final Observable<HttpObject> fullRequest);
}
