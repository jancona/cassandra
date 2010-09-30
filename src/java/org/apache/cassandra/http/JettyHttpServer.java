/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.http;

import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;

public class JettyHttpServer implements IHttpServer
{
    private final Server server;
    
    public JettyHttpServer(InetAddress addr, int port) throws IOException
    {
        // todo: how to get it to bind to a specific interface?
        server = new Server(port);
    }
    
    public void start()
    {
        try 
        {
            server.start();
        } 
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
    
    public void stop()
    {
        try
        {
            server.stop();
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public void init(final CHttpServer cassandra)
    {
        // there are two ways I could have gone about handling requests.
        // 1) a single handler that does everything (no servlet)
        // 2) separate contexts and servlets (enterprise!).
        // I choose 1.
        Handler handler = new AbstractHandler()
        {
            @Override
            public void handle(String s, HttpServletRequest req, HttpServletResponse res, int i) throws IOException, ServletException
            {
                // maybe I don't like this approach because it chokes on favico, etc.
                String handlerName = s.substring(1, s.indexOf('/', 1));
                cassandra.getHandler(CHttpServer.HandlerType.valueOf(handlerName)).handle(new JettyHttp(req, res));
            }
        };
        server.setHandler(handler);
    }
    
    private class JettyHttp implements IHTTP
    {
        private final HttpServletRequest req;
        private final HttpServletResponse res;
        
        JettyHttp(HttpServletRequest req, HttpServletResponse res)
        {
            this.req = req;
            this.res = res;
        }
        
        public String getRequestPath()
        {
            return req.getPathInfo();
        }

        public String getRequestQuery()
        {
            return req.getQueryString();
        }

        public void send(int status, String msg) throws IOException
        {
            res.setContentType("application/json");
            res.setStatus(status);
            res.getWriter().append(msg);
            ((Request)req).setHandled(true);
        }
    }
}
