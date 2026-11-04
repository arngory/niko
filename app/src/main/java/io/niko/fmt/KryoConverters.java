package io.niko.fmt;

import androidx.room.TypeConverter;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import java.io.ByteArrayOutputStream;

import io.niko.database.SubscriptionBean;
import io.niko.fmt.http.HttpBean;
import io.niko.fmt.hysteria.HysteriaBean;
import io.niko.fmt.internal.ChainBean;
import io.niko.fmt.mieru.MieruBean;
import io.niko.fmt.naive.NaiveBean;
import io.niko.fmt.shadowsocks.ShadowsocksBean;
import io.niko.proxy.anytls.AnyTLSBean;
import io.niko.proxy.shadowtls.ShadowTLSBean;
import io.niko.fmt.socks.SOCKSBean;
import io.niko.fmt.ssh.SSHBean;
import io.niko.fmt.trojan.TrojanBean;
import io.niko.fmt.trojan_go.TrojanGoBean;
import io.niko.fmt.tuic.TuicBean;
import io.niko.fmt.v2ray.VMessBean;
import io.niko.fmt.wireguard.WireGuardBean;
import io.niko.ktx.KryosKt;
import io.niko.ktx.Logs;
import io.niko.proxy.config.ConfigBean;
import io.niko.proxy.neko.NekoBean;
import io.niko.utils.JavaUtil;

public class KryoConverters {

    private static final byte[] NULL = new byte[0];

    @TypeConverter
    public static byte[] serialize(Serializable bean) {
        if (bean == null) return NULL;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBufferOutput buffer = KryosKt.byteBuffer(out);
        bean.serializeToBuffer(buffer);
        buffer.flush();
        buffer.close();
        return out.toByteArray();
    }

    /** Set while a shared link is read ([strict]): data that ends early is refused, not a half-filled profile. */
    private static final ThreadLocal<Boolean> STRICT = new ThreadLocal<>();

    /** Runs [read] (a link's deserialization) refusing data that is cut short or declares more than it holds. */
    public static <R> R strict(java.util.concurrent.Callable<R> read) throws Exception {
        Boolean outer = STRICT.get();
        STRICT.set(true);
        try {
            return read.call();
        } finally {
            STRICT.set(outer);
        }
    }

    public static <T extends Serializable> T deserialize(T bean, byte[] bytes) {
        if (bytes == null) return bean;
        // sizes checked against the data before Kryo allocates for them (a shared link may declare gigabytes)
        ByteBufferInput buffer = new BoundedKryoInput(bytes);
        try {
            bean.deserializeFromBuffer(buffer);
        } catch (KryoException e) {
            if (Boolean.TRUE.equals(STRICT.get())) {
                throw new IllegalArgumentException("corrupt data: " + e.getMessage());
            }
            Logs.INSTANCE.w(e);
        } catch (OutOfMemoryError e) {
            // an allocation no check above caught; it failed alone, so this is refused data, not a full heap
            throw new IllegalArgumentException("corrupt data: a field larger than the data");
        }
        bean.initializeDefaultValues();
        return bean;
    }

    @TypeConverter
    public static SOCKSBean socksDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new SOCKSBean(), bytes);
    }

    @TypeConverter
    public static HttpBean httpDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new HttpBean(), bytes);
    }

    @TypeConverter
    public static ShadowsocksBean shadowsocksDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new ShadowsocksBean(), bytes);
    }

    @TypeConverter
    public static ConfigBean configDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new ConfigBean(), bytes);
    }

    @TypeConverter
    public static VMessBean vmessDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new VMessBean(), bytes);
    }

    @TypeConverter
    public static TrojanBean trojanDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new TrojanBean(), bytes);
    }

    @TypeConverter
    public static TrojanGoBean trojanGoDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new TrojanGoBean(), bytes);
    }

    @TypeConverter
    public static MieruBean mieruDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new MieruBean(), bytes);
    }

    @TypeConverter
    public static NaiveBean naiveDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new NaiveBean(), bytes);
    }

    @TypeConverter
    public static HysteriaBean hysteriaDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new HysteriaBean(), bytes);
    }

    @TypeConverter
    public static SSHBean sshDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new SSHBean(), bytes);
    }

    @TypeConverter
    public static WireGuardBean wireguardDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new WireGuardBean(), bytes);
    }

    @TypeConverter
    public static TuicBean tuicDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new TuicBean(), bytes);
    }

    @TypeConverter
    public static ShadowTLSBean shadowTLSDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new ShadowTLSBean(), bytes);
    }

    @TypeConverter
    public static AnyTLSBean anyTLSDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new AnyTLSBean(), bytes);
    }


    @TypeConverter
    public static ChainBean chainDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new ChainBean(), bytes);
    }

    @TypeConverter
    public static NekoBean nekoDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new NekoBean(), bytes);
    }

    @TypeConverter
    public static SubscriptionBean subscriptionDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new SubscriptionBean(), bytes);
    }

}
