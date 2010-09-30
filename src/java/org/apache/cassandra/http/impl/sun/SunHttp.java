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

package org.apache.cassandra.http.impl.sun;

import com.sun.net.httpserver.HttpExchange;
import org.apache.cassandra.http.impl.IHTTP;

import java.io.IOException;
import java.io.OutputStream;

public class SunHttp implements IHTTP
{
    private static final String enc = "UTF8";
    private HttpExchange exch;
    
    public SunHttp(HttpExchange exch)
    {
        this.exch = exch;
    }
    
    public String getRequestPath()
    {
        return exch.getRequestURI().getPath();
    }

    public String getRequestQuery()
    {
        return exch.getRequestURI().getQuery();
    }

    public void send(int status, String msg) throws IOException
    {
        exch.sendResponseHeaders(status, msg.length());
        OutputStream out = exch.getResponseBody();
        out.write(msg.getBytes(enc));
        out.close();
    }
}
