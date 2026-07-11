package com.campus.product.ai.client;

import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/** Rejects AI endpoints that could expose credentials to local or non-HTTPS services. */
public final class AiEndpointValidator {

    private AiEndpointValidator() {
    }

    public static String requireSafePublicHttpsUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || uri.getQuery() != null) {
                throw invalid();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isNonPublic(address)) {
                    throw invalid();
                }
            }
            String normalized = uri.toString();
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (BizException e) {
            throw e;
        } catch (IllegalArgumentException | UnknownHostException e) {
            throw invalid();
        }
    }

    private static boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0);
        }
        // IPv6 unique-local fc00::/7 and IPv4-mapped private/reserved addresses.
        int first = bytes[0] & 0xff;
        if ((first & 0xfe) == 0xfc) {
            return true;
        }
        boolean mapped = true;
        for (int i = 0; i < 10; i++) {
            mapped &= bytes[i] == 0;
        }
        mapped &= (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
        if (mapped) {
            byte[] ipv4 = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
            try {
                return isNonPublic(InetAddress.getByAddress(ipv4));
            } catch (UnknownHostException impossible) {
                return true;
            }
        }
        return false;
    }

    private static BizException invalid() {
        return new BizException(ResultCode.BAD_REQUEST.getCode(), "AI API 地址必须是可公开访问的 HTTPS 地址");
    }
}
