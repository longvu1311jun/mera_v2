package mera.mera_v2.pos.mapping;

import java.util.List;

public record SavePosUserBaseLarkRequest(List<PosUserBaseLarkUpdate> updates) {

    public record PosUserBaseLarkUpdate(String id, String baseLark) {
    }
}
