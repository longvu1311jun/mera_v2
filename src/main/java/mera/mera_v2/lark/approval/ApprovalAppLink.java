package mera.mera_v2.lark.approval;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class ApprovalAppLink {
    private ApprovalAppLink() {}

    static String detail(String instanceCode) {
        return "https://applink.larksuite.com/client/mini_program/open"
                + "?appId=cli_9c7cc8a9a9edd105&mode=appCenter"
                + "&path_pc=" + encode("pc/pages/in-process/index?instanceId=" + instanceCode)
                + "&path=" + encode("pages/detail/index?instanceId=" + instanceCode);
    }

    private static String encode(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8);
    }
}
