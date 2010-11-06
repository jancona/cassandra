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

import static org.apache.cassandra.utils.FBUtilities.bytesToHex;

import java.io.IOError;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.CharacterCodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.cassandra.db.*;
import org.apache.cassandra.db.filter.QueryPath;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.service.StorageProxy;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.cassandra.utils.FBUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CHttpServer
{
    private static final Logger logger = LoggerFactory.getLogger(CHttpServer.class);
    private static final String UTF8 = "UTF8";
    
    private final IHandler getHandler;
    private final IHandler setHandler;
    
    public enum HandlerType 
    {
        get("/get"),
        set("/set");
        
        private final String path;
        private HandlerType(String s) 
        {
            path = s;
        }
        
        public String path() 
        {
            return path;
        }
    }
    
    // todo: have a sessionlocal object (like a threadlocal, you know) to keep track of keyspace, authz, authn, etc.
    
    public CHttpServer() 
    {
        getHandler = new IHandler() 
        {
            public void handle(IHTTP exchange) throws IOException
            {
                get(exchange);
            }
        };
        setHandler = new IHandler() 
        {
            public void handle(IHTTP exchange) throws IOException
            {
                set(exchange);
            }
        };
    }
    
    public IHandler getHandler(HandlerType h) 
    {
        switch (h) {
            case get:   return getHandler;
            case set:   return setHandler;
            default:    throw new RuntimeException("Unknown handler " + h);
        }
    }
    
    private void set(IHTTP exch) 
    {
        String[] parts = exch.getRequestPath().split("\\/", -1);
        String keyspace = parts[2];
        String column_family = parts[3];
        String key = parts[4];
        String super_column = parts[5];
        String column = parts[6];
        String value = parts[7];
        String consistency_level = parts[8];
        
        // todo: validation. send a 400 if things don't check out.
        // todo: scheduling.
        // todo: authn/authz
        
        try
        {
//            clientState.hasKeyspaceAccess(Permission.WRITE_VALUE);
            RowMutation rm = new RowMutation(keyspace, ByteBufferUtil.bytes(key));
            rm.add(new QueryPath(column_family, ByteBufferUtil.bytes(super_column), ByteBufferUtil.bytes(column)),
                    ByteBufferUtil.bytes(value), 
                    System.currentTimeMillis());
            StorageProxy.mutate(Arrays.asList(rm), ConsistencyLevel.valueOf(consistency_level));
        }
        catch (Exception ex)
        {
            handleEx(ex, exch);
            logger.error(ex.getMessage(), ex);
        }
        
        
        String msg = "set!";
        send(200, msg, exch);
        logger.debug(msg);
    }
    
    /** executes a get_slice */
    private void get(IHTTP exch) 
    {
        boolean asString = false;
        
        // todo: validate and send a 400 if things don't check out.
        String[] parts = exch.getRequestPath().split("\\/", -1);
        String keyspace = parts[2];
        String column_family = parts[3];
        String key = parts[4];
        String super_column = parts[5];
        String startCol = parts[6];
        String endCol = parts[7];
        String consistency_level = parts[8];
        
        // todo: authn/authz
        
        // I don't care for the asString hack. What would be cool (and correct) is to convert to the comparator type and
        // stringify that.
        String query = exch.getRequestQuery() == null ? "" : exch.getRequestQuery();
        String[] queryParts = query.split("&", -1);
        for (String part : queryParts)
        {
            String[] chunks = part.split("=", -1);
            if ("asString".equals(chunks[0]))
                asString = true;
        }

        // todo: currently ignoring credentials, etc.
//        clientState.setKeyspace(keyspace);
        
        // todo: scheduling.
        
        try 
        {
            QueryPath path = new QueryPath(column_family, ByteBufferUtil.bytes(super_column), null);
            ReadCommand command = new SliceFromReadCommand(keyspace, ByteBufferUtil.bytes(key), path, ByteBufferUtil.bytes(startCol), ByteBufferUtil.bytes(endCol), false, 100);
            List<Row> rows = StorageProxy.readProtocol(Arrays.asList(command), ConsistencyLevel.valueOf(consistency_level));
            
            StringBuilder json = new StringBuilder();
            json.append("{");
            boolean first = true;
            for (Row row : rows)
            {
                if (row.cf == null)
                    continue;
                
                final String keyDisp = asString ? key : FBUtilities.bytesToHex(row.key.key);
                final AbstractType comparator = row.cf.getComparator();
                if (!first)
                    json.append(", ");
                else
                    first = false;
                json.append(JSON.asKey(keyDisp));
                json.append(" {");
                if (row.cf.isSuper()) {
                    for (IColumn sc : row.cf.getSortedColumns()) {
                        String scDisp = asString ? FBUtilities.decodeToUTF8(sc.name()) : FBUtilities.bytesToHex(sc.name()); 
                        // name
                        json.append(JSON.asKey(scDisp));
                        json.append("{");
                        // deletedat
                        json.append(JSON.asKey("deletedAt"));
                        json.append(sc.getMarkedForDeleteAt());
                        json.append(", ");
                        // subcols
                        json.append(JSON.asKey("subColumns"));
                        json.append(JSON.serializeColumns(sc.getSubColumns(), comparator, asString));
                        json.append("}");
                    }
                } 
                else 
                {
                    json.append(JSON.serializeColumns(row.cf.getSortedColumns(), comparator, asString));
                }
                json.append("}");
            }
            json.append("}");
            
            // todo: the toString() call creates a second buffer. This is inefficient and generates a lot of garbage.
            send(200, json.toString(), exch);
            
        } 
        catch (Exception ex) 
        {
            handleEx(ex, exch);
            logger.error(ex.getMessage(), ex);
        }
    }
    
    private static void send(int status, String msg, IHTTP exch) 
    {
        try 
        {
            exch.send(status, msg);
        } 
        catch (IOException ex) 
        {
            throw new IOError(ex);
        }
    }
    
    /** handling an exception involves deciding what status to send back and then sending it. */
    private void handleEx(Exception ex, IHTTP http) 
    {
        int status = 500; // default status.
        if (ex instanceof UnsupportedEncodingException)
            status = 500;
        else if (ex instanceof IOException)
            status = 500;
        else if (ex instanceof UnavailableException)
            status = 503; // service unavailable
        else if (ex instanceof TimeoutException)
            status = 408; // request timeout
        else if (ex instanceof InvalidRequestException)
            status = 406; // not acceptable.
        else
            logger.warn("Unexpected exception " + ex.getClass().getName());
        send(status, ex.getMessage(), http);
    }
    
    /** utilities for producing JSON. Mostly cribbed from SSTableExport. */
    private static class JSON
    {
    
        private static CharSequence quote(String val)
        {
            return String.format("\"%s\"", val);
        }
        
        private static CharSequence asKey(String val)
        {
            return String.format("%s: ", quote(val));
        }
        
        private static CharSequence serializeColumns(Collection<IColumn> cols, AbstractType comp, boolean asString)
        {
            StringBuilder json = new StringBuilder("[");
            
            Iterator<IColumn> iter = cols.iterator();
            while (iter.hasNext())
            {
                try {
                    json.append("[");
                    IColumn column = iter.next();
                    json.append(quote(asString ? FBUtilities.decodeToUTF8(column.name()) : bytesToHex(column.name())));
                    json.append(", ");
                    json.append(quote(asString ? FBUtilities.decodeToUTF8(column.value()) : bytesToHex(column.value())));
                    json.append(", ");
                    json.append(column.timestamp());
                    json.append(", ");
                    json.append(column.isMarkedForDelete());
                    json.append("]");
                    if (iter.hasNext())
                        json.append(", ");
                }
                catch (CharacterCodingException bollocks)
                {
                    throw new RuntimeException(bollocks);
                }
            }
            
            json.append("]");
            
            return json;
        }
    }
}
