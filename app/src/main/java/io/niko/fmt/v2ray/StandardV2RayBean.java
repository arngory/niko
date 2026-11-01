package io.niko.fmt.v2ray;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import io.niko.fmt.AbstractBean;
import io.niko.fmt.trojan.TrojanBean;
import io.niko.utils.JavaUtil;

public abstract class StandardV2RayBean extends AbstractBean {

    public String uuid;
    public String encryption; // or VLESS flow

    //////// End of VMess & VLESS ////////

    // "V2Ray Transport" tcp/http/ws/quic/grpc/httpupgrade
    public String type;

    public String host;

    public String path;

    // --------------------------------------- tls?

    public String security;

    public String sni;

    public String alpn;

    public String utlsFingerprint;

    public Boolean allowInsecure;

    // --------------------------------------- reality


    public String realityPubKey;

    public String realityShortId;
    // version 6: "" = as the fingerprint carries it, "hybrid", "classical"
    public String realityKeyShare;
    // version 7: VLESS post-quantum encryption spec ("mlkem768x25519plus..."), "" = none
    public String vlessEncryption;
    // version 8: the server's ML-DSA-65 public key for REALITY (Xray mldsa65Verify), "" = off
    public String realityMldsa65Verify;


    // --------------------------------------- //

    public Integer wsMaxEarlyData;
    public String earlyDataHeaderName;

    // XHTTP (version 5): auto, packet-up, stream-up, stream-one; extra is Xray's "extra" JSON
    public String xhttpMode;
    public String xhttpExtra;

    public String certificates;

    // --------------------------------------- ech

    public Boolean enableECH;

    public String echConfig;

    // --------------------------------------- Mux

    public Boolean enableMux;
    public Boolean muxPadding;
    public Integer muxType;
    public Integer muxConcurrency;


    // --------------------------------------- //

    public Integer packetEncoding; // 1:packet 2:xudp

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        if (JavaUtil.isNullOrBlank(uuid)) uuid = "";

        if (JavaUtil.isNullOrBlank(type)) type = "tcp";
        else if ("h2".equals(type)) type = "http";

        type = type.toLowerCase();

        if (JavaUtil.isNullOrBlank(host)) host = "";
        if (JavaUtil.isNullOrBlank(path)) path = "";

        if (JavaUtil.isNullOrBlank(security)) {
            if (this instanceof TrojanBean) {
                security = "tls";
            } else {
                security = "none";
            }
        }
        if (JavaUtil.isNullOrBlank(sni)) sni = "";
        if (JavaUtil.isNullOrBlank(alpn)) alpn = "";

        if (JavaUtil.isNullOrBlank(certificates)) certificates = "";
        if (JavaUtil.isNullOrBlank(earlyDataHeaderName)) earlyDataHeaderName = "";
        if (JavaUtil.isNullOrBlank(utlsFingerprint)) utlsFingerprint = "";

        if (wsMaxEarlyData == null) wsMaxEarlyData = 0;
        if (allowInsecure == null) allowInsecure = false;
        if (packetEncoding == null) packetEncoding = 0;

        if (realityPubKey == null) realityPubKey = "";
        if (realityShortId == null) realityShortId = "";
        if (realityKeyShare == null) realityKeyShare = "";
        if (vlessEncryption == null) vlessEncryption = "";
        if (realityMldsa65Verify == null) realityMldsa65Verify = "";

        if (enableECH == null) enableECH = false;
        if (JavaUtil.isNullOrBlank(echConfig)) echConfig = "";

        if (enableMux == null) enableMux = false;
        if (muxPadding == null) muxPadding = false;
        if (muxType == null) muxType = 0;
        if (muxConcurrency == null) muxConcurrency = 1;

        if (JavaUtil.isNullOrBlank(xhttpMode)) xhttpMode = "auto";
        if (xhttpExtra == null) xhttpExtra = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(8);
        super.serialize(output);
        output.writeString(uuid);
        output.writeString(encryption);
        if (this instanceof VMessBean) {
            output.writeInt(((VMessBean) this).alterId);
        }

        output.writeString(type);
        switch (type) {
            case "tcp":
            case "quic": {
                break;
            }
            case "ws": {
                output.writeString(host);
                output.writeString(path);
                output.writeInt(wsMaxEarlyData);
                output.writeString(earlyDataHeaderName);
                break;
            }
            case "http":
            case "httpupgrade": {
                output.writeString(host);
                output.writeString(path);
                break;
            }
            case "xhttp": {
                output.writeString(host);
                output.writeString(path);
                output.writeString(xhttpMode);
                output.writeString(xhttpExtra);
                break;
            }
            case "grpc": {
                output.writeString(path);
                break;
            }
        }

        output.writeString(security);
        if ("tls".equals(security)) {
            output.writeString(sni);
            output.writeString(alpn);
            output.writeString(certificates);
            output.writeBoolean(allowInsecure);
            output.writeString(utlsFingerprint);
            output.writeString(realityPubKey);
            output.writeString(realityShortId);
        }

        output.writeBoolean(enableECH);
        output.writeString(echConfig);

        output.writeInt(packetEncoding);

        output.writeBoolean(enableMux);
        output.writeBoolean(muxPadding);
        output.writeInt(muxType);
        output.writeInt(muxConcurrency);

        output.writeString(realityKeyShare == null ? "" : realityKeyShare);
        output.writeString(vlessEncryption == null ? "" : vlessEncryption);
        output.writeString(realityMldsa65Verify == null ? "" : realityMldsa65Verify);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        uuid = input.readString();
        encryption = input.readString();
        if (this instanceof VMessBean) {
            ((VMessBean) this).alterId = input.readInt();
        }

        type = input.readString();
        if (version < 5 && ("xhttp".equals(type) || "splithttp".equals(type))) {
            // links stored the type as is before version 5, but no transport fields for it
            type = "xhttp";
        } else switch (type) {
            case "tcp":
            case "quic": {
                break;
            }
            case "ws": {
                host = input.readString();
                path = input.readString();
                wsMaxEarlyData = input.readInt();
                earlyDataHeaderName = input.readString();
                break;
            }
            case "http":
            case "httpupgrade": {
                host = input.readString();
                path = input.readString();
                break;
            }
            case "xhttp": {
                host = input.readString();
                path = input.readString();
                xhttpMode = input.readString();
                xhttpExtra = input.readString();
                break;
            }
            case "grpc": {
                path = input.readString();
                if (version < 4) {
                    // 解决老版本数据的读取问题
                    input.readString();
                    input.readString();
                }
                break;
            }
        }

        security = input.readString();
        if ("tls".equals(security)) {
            sni = input.readString();
            alpn = input.readString();
            certificates = input.readString();
            allowInsecure = input.readBoolean();
            utlsFingerprint = input.readString();
            realityPubKey = input.readString();
            realityShortId = input.readString();
        }

        if (version >= 1) {
            enableECH = input.readBoolean();
            if (version >= 3) {
                echConfig = input.readString();
            } else {
                if (enableECH) {
                    input.readBoolean();
                    input.readBoolean();
                    echConfig = input.readString();
                }
            }
        } else if (version == 0) {
            // 从老版本升级上来但是 version == 0, 可能有 enableECH 也可能没有，需要做判断
            int position = input.getByteBuffer().position(); // 当前位置

            boolean tmpEnableECH = input.readBoolean();
            int tmpPacketEncoding = input.readInt();

            input.setPosition(position); // 读后归位

            if (tmpPacketEncoding != 1 && tmpPacketEncoding != 2) {
                enableECH = tmpEnableECH;
                if (enableECH) {
                    input.readBoolean();
                    input.readBoolean();
                    echConfig = input.readString();
                }
            } // 否则后一位就是 packetEncoding
        }

        packetEncoding = input.readInt();

        if (version >= 2) {
            enableMux = input.readBoolean();
            muxPadding = input.readBoolean();
            muxType = input.readInt();
            muxConcurrency = input.readInt();
        }
        if (version >= 6) {
            realityKeyShare = input.readString();
        }
        if (version >= 7) {
            vlessEncryption = input.readString();
        }
        if (version >= 8) {
            realityMldsa65Verify = input.readString();
        }
    }

    public boolean isVLESS() {
        if (this instanceof VMessBean) {
            Integer aid = ((VMessBean) this).alterId;
            return aid != null && aid == -1;
        }
        return false;
    }

}
