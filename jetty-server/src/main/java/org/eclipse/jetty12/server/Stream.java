//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty12.server;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Callback;

public interface Stream extends Callback
{
    String getId();

    Content readContent();

    void demandContent();

    void send(MetaData.Response response, boolean last, Callback callback, ByteBuffer... content);

    boolean isPushSupported();

    void push(MetaData.Request request);

    boolean isComplete();

    void upgrade(org.eclipse.jetty.io.Connection connection);

    default Throwable consumeAll()
    {
        while (true)
        {
            // We can always just read again here as EOF and Error content will be persistently returned.
            Content content = readContent();

            // if we cannot read to EOF then fail the stream rather than wait for unconsumed content
            if (content == null)
                return new IOException("Content not consumed");

            // Always release any returned content. This is a noop for EOF and Error content.
            content.release();

            // if the input failed, then fail the stream for same reason
            if (content instanceof Content.Error)
                return ((Content.Error)content).getCause();

            if (content.isLast())
                return null;
        }
    }
}
