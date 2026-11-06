package io.niko.fmt;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import io.niko.ktx.NetsKt;
import io.niko.utils.JavaUtil;

public abstract class AbstractBean extends Serializable {

    public String serverAddress;
    public Integer serverPort;

    public String name;

    //

    public String customOutboundJson;
    public String customConfigJson;

    // port knocking: "7000,8000/udp,9000/tcp,1234/udp:68656c6c6f"
    public String knockSequence;
    public Integer knockInterval; // ms
    public Integer knockTtl; // seconds
    public Integer knockSilence; // seconds without a reply before one re-knock, 0 off
    // TLS fragmentation: 0 off, 1 TLS records, 2 TCP segments
    public Integer tlsFragment;
    // version 4: SHA-256 of the server's public key, base64, one per line
    public String certPins;
    // version 5: DNS tunnel the profile dials through, "" off, "dnstt" or "masterdnsvpn"
    public String dnsTunnel;
    public String dnsTunnelResolver; // dnstt: https://host/dns-query, tls://host, udp://host; masterdnsvpn: IPs, one per line
    public String dnsTunnelDomain; // masterdnsvpn may take several, comma separated
    public String dnsTunnelKey; // dnstt: the server's public key; masterdnsvpn: the shared key
    public Integer dnsTunnelMethod; // masterdnsvpn DATA_ENCRYPTION_METHOD, 0-5
    public Boolean dnsTunnelFixedUpstream; // masterdnsvpn server in TCP mode instead of SOCKS5
    // version 9: raycontrol dnsauth-v1 of a dnstt tunnel, "v1;<node id>;<service id>", "" off
    public String dnsTunnelAuth;
    // version 10: raycontrol dnsseal-v1 of a dnstt tunnel, the zone's key K in base64url, "" off
    public String dnsTunnelSeal;
    // version 11: raycontrol dnszones-v1, the tunnel's spare zones in their order, comma separated, "" none
    public String dnsTunnelSpare;
    // extraVersion 6: UDP desync of QUIC clients (Hysteria2, TUIC), core common/udpdesync
    public Integer udpPrelude; // fake datagrams before the first real one, 0-8
    public String udpPreludeSni; // the fakes are QUIC Initials naming this server; empty = random bytes
    public Integer udpPreludeTtl; // IP TTL of the fakes, 0 = default
    public Boolean udpReorderInitial; // the second QUIC Initial goes first
    // extraVersion 7: raycontrol Yggdrasil profile (niko-ygg): the node as the only peer, this device's key
    public String yggPeer; // tls://<name>:443?key=<node key hex>&sni=<name>; "" off
    public String yggKey; // the device's ed25519 seed, 64 hex digits
    // extraVersion 8: the node's public peers (niko-ygg-via, R4-18), one per line: a fallback when the direct one fails
    public String yggVia;

    //
    public transient String finalAddress;
    public transient int finalPort;

    public String displayName() {
        if (JavaUtil.isNotBlank(name)) {
            return name;
        } else {
            return displayAddress();
        }
    }

    public String displayAddress() {
        return NetsKt.wrapIPV6Host(serverAddress) + ":" + serverPort;
    }

    public String network() {
        return "tcp,udp";
    }

    public boolean canICMPing() {
        return true;
    }

    public boolean canTCPing() {
        return true;
    }

    public boolean canMapping() {
        return true;
    }

    @Override
    public void initializeDefaultValues() {
        if (JavaUtil.isNullOrBlank(serverAddress)) {
            serverAddress = "127.0.0.1";
        } else if (serverAddress.startsWith("[") && serverAddress.endsWith("]")) {
            serverAddress = NetsKt.unwrapIPV6Host(serverAddress);
        }
        if (serverPort == null) {
            serverPort = 1080;
        }
        if (name == null) name = "";

        finalAddress = serverAddress;
        finalPort = serverPort;

        if (customOutboundJson == null) customOutboundJson = "";
        if (customConfigJson == null) customConfigJson = "";
        if (knockSequence == null) knockSequence = "";
        if (knockInterval == null) knockInterval = 200;
        if (knockTtl == null) knockTtl = 10;
        if (knockSilence == null) knockSilence = 15;
        if (tlsFragment == null) tlsFragment = 0;
        if (certPins == null) certPins = "";
        if (dnsTunnel == null) dnsTunnel = "";
        if (dnsTunnelResolver == null) dnsTunnelResolver = "";
        if (dnsTunnelDomain == null) dnsTunnelDomain = "";
        if (dnsTunnelKey == null) dnsTunnelKey = "";
        if (dnsTunnelMethod == null) dnsTunnelMethod = 2;
        if (dnsTunnelFixedUpstream == null) dnsTunnelFixedUpstream = false;
        if (dnsTunnelAuth == null) dnsTunnelAuth = "";
        if (dnsTunnelSeal == null) dnsTunnelSeal = "";
        if (dnsTunnelSpare == null) dnsTunnelSpare = "";
        if (udpPrelude == null) udpPrelude = 0;
        if (udpPreludeSni == null) udpPreludeSni = "";
        if (udpPreludeTtl == null) udpPreludeTtl = 0;
        if (yggPeer == null) yggPeer = "";
        if (yggKey == null) yggKey = "";
        if (yggVia == null) yggVia = "";
        if (udpReorderInitial == null) udpReorderInitial = false;
    }


    private transient boolean serializeWithoutName;

    @Override
    public void serializeToBuffer(@NonNull ByteBufferOutput output) {
        serialize(output);

        output.writeInt(11);
        if (!serializeWithoutName) {
            output.writeString(name);
        }
        output.writeString(customOutboundJson);
        output.writeString(customConfigJson);
        output.writeString(knockSequence);
        output.writeInt(knockInterval != null ? knockInterval : 200);
        output.writeInt(knockTtl != null ? knockTtl : 10);
        output.writeInt(tlsFragment != null ? tlsFragment : 0);
        output.writeInt(knockSilence != null ? knockSilence : 15);
        output.writeString(certPins != null ? certPins : "");
        output.writeString(dnsTunnel != null ? dnsTunnel : "");
        output.writeString(dnsTunnelResolver != null ? dnsTunnelResolver : "");
        output.writeString(dnsTunnelDomain != null ? dnsTunnelDomain : "");
        output.writeString(dnsTunnelKey != null ? dnsTunnelKey : "");
        output.writeInt(dnsTunnelMethod != null ? dnsTunnelMethod : 2);
        output.writeBoolean(dnsTunnelFixedUpstream != null && dnsTunnelFixedUpstream);
        output.writeInt(udpPrelude != null ? udpPrelude : 0);
        output.writeString(udpPreludeSni != null ? udpPreludeSni : "");
        output.writeInt(udpPreludeTtl != null ? udpPreludeTtl : 0);
        output.writeBoolean(udpReorderInitial != null && udpReorderInitial);
        output.writeString(yggPeer != null ? yggPeer : "");
        output.writeString(yggKey != null ? yggKey : "");
        output.writeString(yggVia != null ? yggVia : "");
        output.writeString(dnsTunnelAuth != null ? dnsTunnelAuth : "");
        output.writeString(dnsTunnelSeal != null ? dnsTunnelSeal : "");
        output.writeString(dnsTunnelSpare != null ? dnsTunnelSpare : "");
    }

    @Override
    public void deserializeFromBuffer(@NonNull ByteBufferInput input) {
        deserialize(input);

        int extraVersion = input.readInt();

        name = input.readString();
        customOutboundJson = input.readString();
        customConfigJson = input.readString();
        if (extraVersion >= 2) {
            knockSequence = input.readString();
            knockInterval = input.readInt();
            knockTtl = input.readInt();
            tlsFragment = input.readInt();
        }
        if (extraVersion >= 3) {
            knockSilence = input.readInt();
        }
        if (extraVersion >= 4) {
            certPins = input.readString();
        }
        if (extraVersion >= 5) {
            dnsTunnel = input.readString();
            dnsTunnelResolver = input.readString();
            dnsTunnelDomain = input.readString();
            dnsTunnelKey = input.readString();
            dnsTunnelMethod = input.readInt();
            dnsTunnelFixedUpstream = input.readBoolean();
        }
        if (extraVersion >= 6) {
            udpPrelude = input.readInt();
            udpPreludeSni = input.readString();
            udpPreludeTtl = input.readInt();
            udpReorderInitial = input.readBoolean();
        }
        if (extraVersion >= 7) {
            yggPeer = input.readString();
            yggKey = input.readString();
        }
        if (extraVersion >= 8) {
            yggVia = input.readString();
        }
        if (extraVersion >= 9) {
            dnsTunnelAuth = input.readString();
        }
        if (extraVersion >= 10) {
            dnsTunnelSeal = input.readString();
        }
        if (extraVersion >= 11) {
            dnsTunnelSpare = input.readString();
        }
    }

    public void serialize(ByteBufferOutput output) {
        output.writeString(serverAddress);
        output.writeInt(serverPort);
    }

    public void deserialize(ByteBufferInput input) {
        serverAddress = input.readString();
        serverPort = input.readInt();
    }

    @NotNull
    @Override
    public abstract AbstractBean clone();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        try {
            serializeWithoutName = true;
            ((AbstractBean) o).serializeWithoutName = true;
            return Arrays.equals(KryoConverters.serialize(this), KryoConverters.serialize((AbstractBean) o));
        } finally {
            serializeWithoutName = false;
            ((AbstractBean) o).serializeWithoutName = false;
        }
    }

    @Override
    public int hashCode() {
        try {
            serializeWithoutName = true;
            return Arrays.hashCode(KryoConverters.serialize(this));
        } finally {
            serializeWithoutName = false;
        }
    }

    @NotNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + JavaUtil.gson.toJson(this);
    }
}
