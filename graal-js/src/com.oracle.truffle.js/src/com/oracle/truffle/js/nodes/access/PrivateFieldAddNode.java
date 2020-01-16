/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.objects.JSAttributes;

public abstract class PrivateFieldAddNode extends JavaScriptBaseNode {
    protected final JSContext context;

    public static PrivateFieldAddNode create(JSContext context) {
        return PrivateFieldAddNodeGen.create(context);
    }

    protected PrivateFieldAddNode(JSContext context) {
        this.context = context;
    }

    public abstract void execute(Object target, Object key, Object value);

    @Specialization(guards = {"key == cachedKey", "isJSObject(target)"}, limit = "1")
    void doCachedKey(DynamicObject target, HiddenKey key, Object value,
                    @Cached("key") @SuppressWarnings("unused") HiddenKey cachedKey,
                    @Cached("create(key)") HasHiddenKeyCacheNode hasNode,
                    @Cached("createSetHidden(key, context)") PropertySetNode setNode) {
        if (!hasNode.executeHasHiddenKey(target)) {
            setNode.setValue(target, value);
        } else {
            duplicate(key);
        }
    }

    @TruffleBoundary
    @Specialization(guards = {"isJSObject(target)"}, replaces = "doCachedKey")
    void doUncachedKey(DynamicObject target, HiddenKey key, Object value) {
        if (!target.containsKey(key)) {
            target.define(key, value, JSAttributes.getDefaultNotEnumerable());
        } else {
            duplicate(key);
        }
    }

    @TruffleBoundary
    private Object duplicate(@SuppressWarnings("unused") HiddenKey key) {
        throw Errors.createTypeErrorCannotAddPrivateMember(key.getName(), this);
    }

    @TruffleBoundary
    @Fallback
    void doFallback(@SuppressWarnings("unused") Object target, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value) {
        throw Errors.createTypeErrorCannotSetProperty(key.toString(), target, this);
    }
}
