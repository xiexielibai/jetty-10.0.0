//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.http;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MimeTypesTest
{
    @Test
    public void testGetMimeByExtensionGzip()
    {
        assertMimeTypeByExtension("application/gzip", "test.gz");
    }

    @Test
    public void testGetMimeByExtensionPng()
    {
        assertMimeTypeByExtension("image/png", "test.png");
        assertMimeTypeByExtension("image/png", "TEST.PNG");
        assertMimeTypeByExtension("image/png", "Test.Png");
    }

    @Test
    public void testGetMimeByExtensionPngMultiDot()
    {
        assertMimeTypeByExtension("image/png", "org.eclipse.jetty.Logo.png");
    }

    @Test
    public void testGetMimeByExtensionPngDeepPath()
    {
        assertMimeTypeByExtension("image/png", "/org/eclipse/jetty/Logo.png");
    }

    @Test
    public void testGetMimeByExtensionText()
    {
        assertMimeTypeByExtension("text/plain", "test.txt");
        assertMimeTypeByExtension("text/plain", "TEST.TXT");
    }

    @Test
    public void testGetMimeByExtensionNoExtension()
    {
        MimeTypes mimetypes = new MimeTypes();
        String contentType = mimetypes.getMimeByExtension("README");
        assertNull(contentType);
    }

    private void assertMimeTypeByExtension(String expectedMimeType, String filename)
    {
        MimeTypes mimetypes = new MimeTypes();
        String contentType = mimetypes.getMimeByExtension(filename);
        String prefix = "MimeTypes.getMimeByExtension(" + filename + ")";
        assertNotNull(contentType, prefix);
        assertEquals(expectedMimeType, contentType, prefix);
    }

    private void assertCharsetFromContentType(String contentType, String expectedCharset)
    {
        assertThat("getCharsetFromContentType(\"" + contentType + "\")",
            MimeTypes.getCharsetFromContentType(contentType), is(expectedCharset));
    }

    @Test
    public void testCharsetFromContentType()
    {
        assertCharsetFromContentType("foo/bar;charset=abc;some=else", "abc");
        assertCharsetFromContentType("foo/bar;charset=abc", "abc");
        assertCharsetFromContentType("foo/bar ; charset = abc", "abc");
        assertCharsetFromContentType("foo/bar ; charset = abc ; some=else", "abc");
        assertCharsetFromContentType("foo/bar;other=param;charset=abc;some=else", "abc");
        assertCharsetFromContentType("foo/bar;other=param;charset=abc", "abc");
        assertCharsetFromContentType("foo/bar other = param ; charset = abc", "abc");
        assertCharsetFromContentType("foo/bar other = param ; charset = abc ; some=else", "abc");
        assertCharsetFromContentType("foo/bar other = param ; charset = abc", "abc");
        assertCharsetFromContentType("foo/bar other = param ; charset = \"abc\" ; some=else", "abc");
        assertCharsetFromContentType("foo/bar", null);
        assertCharsetFromContentType("foo/bar;charset=uTf8", "utf-8");
        assertCharsetFromContentType("foo/bar;other=\"charset=abc\";charset=uTf8", "utf-8");
        assertCharsetFromContentType("application/pdf; charset=UTF-8", "utf-8");
        assertCharsetFromContentType("application/pdf;; charset=UTF-8", "utf-8");
        assertCharsetFromContentType("application/pdf;;; charset=UTF-8", "utf-8");
        assertCharsetFromContentType("application/pdf;;;; charset=UTF-8", "utf-8");
        assertCharsetFromContentType("text/html;charset=utf-8", "utf-8");
    }

    @Test
    public void testContentTypeWithoutCharset()
    {
        assertEquals("foo/bar;some=else", MimeTypes.getContentTypeWithoutCharset("foo/bar;charset=abc;some=else"));
        assertEquals("foo/bar", MimeTypes.getContentTypeWithoutCharset("foo/bar;charset=abc"));
        assertEquals("foo/bar", MimeTypes.getContentTypeWithoutCharset("foo/bar ; charset = abc"));
        assertEquals("foo/bar;some=else", MimeTypes.getContentTypeWithoutCharset("foo/bar ; charset = abc ; some=else"));
        assertEquals("foo/bar;other=param;some=else", MimeTypes.getContentTypeWithoutCharset("foo/bar;other=param;charset=abc;some=else"));
        assertEquals("foo/bar;other=param", MimeTypes.getContentTypeWithoutCharset("foo/bar;other=param;charset=abc"));
        assertEquals("foo/bar ; other = param", MimeTypes.getContentTypeWithoutCharset("foo/bar ; other = param ; charset = abc"));
        assertEquals("foo/bar ; other = param;some=else", MimeTypes.getContentTypeWithoutCharset("foo/bar ; other = param ; charset = abc ; some=else"));
        assertEquals("foo/bar ; other = param", MimeTypes.getContentTypeWithoutCharset("foo/bar ; other = param ; charset = abc"));
        assertEquals("foo/bar ; other = param;some=else", MimeTypes.getContentTypeWithoutCharset("foo/bar ; other = param ; charset = \"abc\" ; some=else"));
        assertEquals("foo/bar", MimeTypes.getContentTypeWithoutCharset("foo/bar"));
        assertEquals("foo/bar", MimeTypes.getContentTypeWithoutCharset("foo/bar;charset=uTf8"));
        assertEquals("foo/bar;other=\"charset=abc\"", MimeTypes.getContentTypeWithoutCharset("foo/bar;other=\"charset=abc\";charset=uTf8"));
        assertEquals("text/html", MimeTypes.getContentTypeWithoutCharset("text/html;charset=utf-8"));
    }
}
