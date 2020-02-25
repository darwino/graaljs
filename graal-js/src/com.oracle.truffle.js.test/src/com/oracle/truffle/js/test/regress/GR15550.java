/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.test.regress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.builtins.JSSlowArray;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.test.JSTest;

public class GR15550 {
    @Test
    public void testDefinePropertyLengthGreaterThanInt32() {
        try (Context context = JSTest.newContextBuilder().build()) {
            Value arr = context.eval(JavaScriptLanguage.ID, "var arr = [1,2,3,6,7,8]; arr;");
            // Make it a "slow array"
            context.eval(JavaScriptLanguage.ID, "Object.defineProperty(arr, 3, { value: 55 });");
            assertTrue("expected SlowArray", JSSlowArray.isJSSlowArray(JSObject.get(JavaScriptLanguage.getJSRealm(context).getGlobalObject(), "arr")));
            // defineProperty with length > 2**31
            context.eval(JavaScriptLanguage.ID, "Object.defineProperty(arr, 'length', { value: 4294967289 });");
            assertEquals(4294967289L, arr.getArraySize());
            // defineProperty with length < current length
            context.eval(JavaScriptLanguage.ID, "Object.defineProperty(arr, 'length', { value: 4294967285 });");
            assertEquals(4294967285L, arr.getArraySize());

            context.eval(JavaScriptLanguage.ID, "Object.defineProperty(arr, 4294967283, { value: 66 });");
            try {
                context.eval(JavaScriptLanguage.ID, "Object.defineProperty(arr, 'length', { value: 4294967282 });");
                fail();
            } catch (PolyglotException e) {
                assertTrue(e.isGuestException());
            }
            assertEquals(4294967284L, arr.getArraySize());
        }
    }
}
