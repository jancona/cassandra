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

import org.mortbay.jetty.Request;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;

public class JettyServletServer extends JettyHttpServer
{   
    public JettyServletServer(InetAddress addr, int port) throws IOException
    {
        super(addr, port);
    }

    public void init(final CHttpServer cassandra)
    {
        // set up the contexts, servlets, etc here.
        for (CHttpServer.HandlerType info : CHttpServer.HandlerType.values()) 
        {
            final IHandler handler = cassandra.getHandler(info); 
            final String op = info.path();
            Servlet servlet = new HttpServlet() 
            {
                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
                {
                    handler.handle(new JettyHttp(op, req, resp));                      
                }
            };
            Context ctx = new Context(server, info.path(), Context.NO_SESSIONS);
            ctx.addServlet(new ServletHolder(servlet), "/*");
        }
    }
    
    private class JettyHttp implements IHTTP
    {
        private final String op;
        private final HttpServletRequest req;
        private final HttpServletResponse res;
        
        JettyHttp(String op, HttpServletRequest req, HttpServletResponse res)
        {
            this.op = op;
            this.req = req;
            this.res = res;
        }
        
        public String getRequestPath()
        {
            return op + req.getPathInfo();
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
