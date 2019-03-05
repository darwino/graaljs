/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.nodes.access;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.ReadNode;
import com.oracle.truffle.js.nodes.instrumentation.JSTags;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.ReadPropertyExpressionTag;
import com.oracle.truffle.js.runtime.JSContext;

public class GlobalConstantNode extends JSTargetableNode implements ReadNode {

    @Child private GlobalObjectNode globalObjectNode;
    @Child private JSConstantNode constantNode;
    private final String propertyName;

    protected GlobalConstantNode(JSContext context, String propertyName, JSConstantNode constantNode) {
        this.globalObjectNode = GlobalObjectNode.create(context);
        this.constantNode = constantNode;
        this.propertyName = propertyName;
    }

    public static JSTargetableNode createGlobalConstant(JSContext ctx, String propertyName, Object value) {
        return new GlobalConstantNode(ctx, propertyName, JSConstantNode.create(value));
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == ReadPropertyExpressionTag.class) {
            return true;
        } else {
            return super.hasTag(tag);
        }
    }

    @Override
    public Object getNodeObject() {
        return JSTags.createNodeObjectDescriptor("key", propertyName);
    }

    @Override
    public Object executeWithTarget(VirtualFrame frame, Object target) {
        return execute(frame);
    }

    @Override
    public Object evaluateTarget(VirtualFrame frame) {
        return globalObjectNode.executeDynamicObject();
    }

    @Override
    public JavaScriptNode getTarget() {
        return globalObjectNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return constantNode.execute(frame);
    }

    @Override
    public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
        return constantNode.executeInt(frame);
    }

    @Override
    public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        return constantNode.executeDouble(frame);
    }

    public Object getValue() {
        return constantNode.getValue();
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return String.format("%s(property=%s, value=%s)", super.toString(), propertyName, constantNode.getValue());
    }

    @Override
    protected JavaScriptNode copyUninitialized() {
        return new GlobalConstantNode(globalObjectNode.getContext(), propertyName, cloneUninitialized(constantNode));
    }

    static final class LineNumberNode extends JSConstantNode {
        LineNumberNode() {
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return getLineNumber();
        }

        @Override
        public int executeInt(VirtualFrame frame) {
            return getLineNumber();
        }

        @Override
        public double executeDouble(VirtualFrame frame) {
            return getLineNumber();
        }

        private int getLineNumber() {
            return getEncapsulatingSourceSection().getStartLine();
        }

        @Override
        public Object getValue() {
            return getLineNumber();
        }
    }

    static final class FileNameNode extends JSConstantNode {
        FileNameNode() {
        }

        @Override
        public String execute(VirtualFrame frame) {
            return getFileName();
        }

        @Override
        public String executeString(VirtualFrame frame) {
            return getFileName();
        }

        private String getFileName() {
            Source source = getEncapsulatingSourceSection().getSource();
            String path = source.getPath();
            return (path == null) ? source.getName() : path;
        }

        @Override
        public Object getValue() {
            return getFileName();
        }
    }

    static final class DirNameNode extends JSConstantNode {
        private final JSContext context;

        DirNameNode(JSContext context) {
            this.context = context;
        }

        @Override
        public String execute(VirtualFrame frame) {
            return getDirName();
        }

        @Override
        public String executeString(VirtualFrame frame) {
            return getDirName();
        }

        @TruffleBoundary
        private String getDirName() {
            Source source = getEncapsulatingSourceSection().getSource();
            if (source.isInternal() || source.isInteractive()) {
                return "";
            }
            String path = source.getPath();
            path = (path == null) ? source.getName() : path;
            if (path.startsWith("file:")) {
                path = path.substring("file:".length());
            }
            String fileSeparator = context.getRealm().getEnv().getFileNameSeparator();
            if (fileSeparator.equals("\\") && path.startsWith("/")) {
                // on Windows, remove first "/" from /c:/test/dir/ style paths
                path = path.substring(1);
            }
            Path filePath = Paths.get(path).toAbsolutePath();
            Path parentPath = filePath.getParent();
            String dirPath = (parentPath == null) ? "" : parentPath.toString();
            if (!dirPath.isEmpty() && !(dirPath.charAt(dirPath.length() - 1) == '/' || fileSeparator.equals(String.valueOf(dirPath.charAt(dirPath.length() - 1))))) {
                dirPath += fileSeparator;
            }
            return dirPath;
        }

        @Override
        public Object getValue() {
            return getDirName();
        }
    }
}
