package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {
    public XC_MethodHook() {}

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;

        public Object getResult() { throw new UnsupportedOperationException("stub"); }
        public void setResult(Object result) { throw new UnsupportedOperationException("stub"); }
        public Throwable getThrowable() { throw new UnsupportedOperationException("stub"); }
        public boolean hasThrowable() { throw new UnsupportedOperationException("stub"); }
    }

    public class Unhook {
        public Member getHookedMethod() { throw new UnsupportedOperationException("stub"); }
        public void unhook() { throw new UnsupportedOperationException("stub"); }
    }
}
