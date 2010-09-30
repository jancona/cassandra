/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * 
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.http.impl.sun;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.cassandra.http.CHttpServer;
import org.apache.cassandra.http.impl.IHttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/** Wraps internal sun http server for use by cassandra. */
public class SunHttpServer implements IHttpServer
{
    private final HttpServer http;
    public SunHttpServer(InetAddress host, int port) throws IOException
    {
        http = HttpServer.create(new InetSocketAddress(host, port), port);
        http.setExecutor(null);
    }
    
    public void init(final CHttpServer cassandra)
    {
        // iterate over the cassandra-supplied handlers and create sun contexts for them. */
        for (CHttpServer.Handler info : CHttpServer.Handler.values()) 
        {
            final CHttpServer.Handler finfo = info;
            http.createContext(info.path(),
                               new HttpHandler() 
                               {
                                   public void handle(HttpExchange httpExchange) throws IOException
                                   {
                                       cassandra.getHandler(finfo).handle(new SunHttp(httpExchange));
                                   }
                               }
            );
        }
    }

    public void start()
    {
        http.start();
    }

    public void stop()
    {
        http.stop(0);
    }
}
