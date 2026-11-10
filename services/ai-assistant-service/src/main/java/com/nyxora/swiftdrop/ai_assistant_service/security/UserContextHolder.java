package com.nyxora.swiftdrop.ai_assistant_service.security;

/**
 * Thread-safe holder for the authenticated user identity during request execution.
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserIdentity> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {}

    public static void setContext(UserIdentity identity) {
        CONTEXT.set(identity);
    }

    public static UserIdentity getContext() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

