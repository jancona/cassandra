/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.http;

import com.sun.net.httpserver.HttpServer;
import org.apache.cassandra.service.AbstractCassandraDaemon;

import java.io.IOException;
import java.net.InetSocketAddress;

public class HttpDaemon extends AbstractCassandraDaemon
{
    
    private HttpServer http;
    private CHttpServer rit;
    
    @Override
    public void start() throws IOException
    {
        http.start();
    }

    @Override
    public void stop()
    {
        http.start();
    }

    @Override
    protected void setup() throws IOException
    {
        super.setup();
        try 
        {
            rit = new CHttpServer();
            http = HttpServer.create(new InetSocketAddress(listenAddr, listenPort), listenPort);
            http.setExecutor(null);
            for (CHttpServer.Handler handler : CHttpServer.Handler.values()) 
                http.createContext(handler.path(), rit.getHandler(handler));
        } 
        catch (IOException wtf) 
        {
            throw new RuntimeException(wtf);
        }
    }

    public static void main(String args[]) 
    {
        new HttpDaemon().activate();
    }
}
