package com.blckswan.blckchat;

/** Android JNI bridge into the portable BLCKCHAT C11 SMS-framing library.
 * No plaintext, key or cryptographic primitive enters this codec. */
public final class NativeSmsCodec {
    static { System.loadLibrary("blcknative"); }
    private NativeSmsCodec() {}
    public static native String pack(byte[] alreadySealed, String randomMessageId);
    public static native byte[] unpack(String completeSmsText);
}
