package io.niko;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.ToNumberPolicy;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.niko.utils.Util;

public class SingBoxOptions {

    // base

    private static final Gson gsonSingbox = new GsonBuilder()
            .registerTypeAdapterFactory(new SingBoxOptionAdapterFactory())
            .setPrettyPrinting()
            .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setLenient()
            .disableHtmlEscaping()
            .create();

    public static class SingBoxOption {

        public transient Map<String, Object> _hack_config_map; // 仍然用普通json方式合并，所以Object内不要使用 _hack

        public transient String _hack_custom_config;

        public SingBoxOption() {
            _hack_config_map = new HashMap<>();
        }

        public Map<String, Object> asMap() {
            return gsonSingbox.fromJson(gsonSingbox.toJson(this), Map.class);
        }

    }

    public static final class CustomSingBoxOption extends SingBoxOption {

        public transient String config;

        public CustomSingBoxOption(String config) {
            super();
            this.config = config;
        }

        public Map<String, Object> getBasicMap() {
            Map<String, Object> map = gsonSingbox.fromJson(config, Map.class);
            if (map == null) {
                map = new HashMap<>();
            }
            return map;
        }
    }

    // 自定义序列化器: the reflective output of an option, merged with its _hack fields.
    // A registered factory, so getDelegateAdapter() reliably skips to the reflective adapter.
    public static class SingBoxOptionAdapterFactory implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (!SingBoxOption.class.isAssignableFrom(type.getRawType())) return null;
            TypeAdapterFactory self = this;
            TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);
            TypeAdapter<T> declaredDelegate = gson.getDelegateAdapter(this, type);
            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                        return;
                    }
                    SingBoxOption src = (SingBoxOption) value;
                    Map<String, Object> map;
                    if (src instanceof CustomSingBoxOption) {
                        map = ((CustomSingBoxOption) src).getBasicMap();
                    } else {
                        // the runtime class, not the declared one: outbounds are List<SingBoxOption>
                        @SuppressWarnings("unchecked")
                        TypeAdapter<SingBoxOption> delegate = (TypeAdapter<SingBoxOption>)
                                gson.getDelegateAdapter(self, TypeToken.get(src.getClass()));
                        map = gson.fromJson(delegate.toJson(src), Map.class);
                    }
                    if (src._hack_config_map != null && !src._hack_config_map.isEmpty()) {
                        Util.INSTANCE.mergeMap(map, src._hack_config_map);
                    }
                    if (src._hack_custom_config != null && !src._hack_custom_config.isBlank()) {
                        Util.INSTANCE.mergeJSON(map, src._hack_custom_config);
                    }
                    elementAdapter.write(out, gson.toJsonTree(map));
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    return declaredDelegate.read(in);
                }
            };
        }
    }

    // custom classes

    public static class User {
        public String username;
        public String password;
    }

    public static class MyOptions extends SingBoxOption {
        public LogOptions log;

        public DNSOptions dns;

        public NTPOptions ntp;

        public List<Inbound> inbounds;

        public List<SingBoxOption> outbounds;

        public List<SingBoxOption> endpoints;

        public RouteOptions route;

        public ExperimentalOptions experimental;

    }

    // paste generate output here

    public static class ClashAPIOptions extends SingBoxOption {

        public String external_controller;

        public String external_ui;

        public String external_ui_download_url;

        public String external_ui_download_detour;

        public String secret;

        public String default_mode;

        // Generate note: option type:  public List<String> ModeList;


        // added from sing-box option.ClashAPIOptions
        public List<String> access_control_allow_origin;

        public Boolean access_control_allow_private_network;

        public String cache_file;

        public String cache_id;

        public Boolean store_fakeip;

        public Boolean store_mode;

        public Boolean store_selected;

    }

    public static class SelectorOutboundOptions extends SingBoxOption {

        public List<String> outbounds;

        @SerializedName("default")
        public String default_;

    }

    public static class URLTestOutboundOptions extends SingBoxOption {

        public List<String> outbounds;

        public String url;

        public String interval;

        public Integer tolerance;

    }


    public static class Options extends SingBoxOption {

        public String $schema;

        public LogOptions log;

        public DNSOptions dns;

        public NTPOptions ntp;

        public List<Inbound> inbounds;

        public List<Outbound> outbounds;

        public RouteOptions route;

        public ExperimentalOptions experimental;

    }

    public static class LogOptions extends SingBoxOption {

        public Boolean disabled;

        public String level;

        public String output;

        public Boolean timestamp;

        // Generate note: option type:  public Boolean DisableColor;

    }

    public static class DebugOptions extends SingBoxOption {

        public String listen;

        public Integer gc_percent;

        public Integer max_stack;

        public Integer max_threads;

        public Boolean panic_on_fault;

        public String trace_back;

        public Long memory_limit;

        public Boolean oom_killer;

    }


    public static class DirectInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public String network;

        public String override_address;

        public Integer override_port;

    }

    public static class DirectOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        public String override_address;

        public Integer override_port;

        public Integer proxy_protocol;

    }

    public static class DNSOptions extends SingBoxOption {

        public List<DNSServerOptions> servers;

        public List<DNSRule> rules;

        @SerializedName("final")
        public String final_;

        public Boolean reverse_mapping;

        // Generate note: nested type DNSClientOptions
        public String strategy;

        public Boolean disable_cache;

        public Boolean disable_expire;

        public Boolean independent_cache;

        // End of public DNSClientOptions ;

    }

    public static class DNSServerOptions extends SingBoxOption {

        public String type;
        public String tag;
        public String server;
        public Integer server_port;
        public String path;
        public String domain_resolver;
        public String detour;
        public String inet4_range;
        public String inet6_range;

        public static DNSServerOptions fromAddress(String address, String tag,
                                                    String detour, String resolver) {
            DNSServerOptions options = new DNSServerOptions();
            options.tag = tag;
            options.detour = detour;
            options.domain_resolver = resolver;
            if ("local".equals(address) || address.startsWith("dhcp://")) {
                // the system resolver already uses the DHCP-provided servers on Android
                options.type = "local";
                options.domain_resolver = null;
                return options;
            }
            String uriAddress = address;
            if (!address.contains("://")) {
                if (address.indexOf(':') != address.lastIndexOf(':') && !address.startsWith("[")) {
                    uriAddress = "[" + address + "]";
                }
                uriAddress = "udp://" + uriAddress;
            }
            URI uri = URI.create(uriAddress);
            options.type = uri.getScheme();
            switch (options.type) {
                case "udp": case "tcp": case "tls": case "quic": case "https": case "h3":
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported DNS transport: " + options.type);
            }
            options.server = uri.getHost();
            if (options.server == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Invalid DNS server address");
            }
            if (options.server.startsWith("[")) {
                options.server = options.server.substring(1, options.server.length() - 1);
            }
            if (uri.getPort() > 65535 || uri.getPort() == 0) {
                throw new IllegalArgumentException("Invalid DNS server port");
            }
            if (uri.getPort() != -1) options.server_port = uri.getPort();
            if ("https".equals(options.type) || "h3".equals(options.type)) {
                options.path = uri.getRawPath();
                if (uri.getRawQuery() != null) options.path += "?" + uri.getRawQuery();
            } else if ((uri.getRawPath() != null && !uri.getRawPath().isEmpty()) || uri.getRawQuery() != null) {
                throw new IllegalArgumentException("DNS transport does not support a URL path");
            }
            return options;
        }
    }

    public static class DNSClientOptions extends SingBoxOption {

        public String strategy;

        public Boolean disable_cache;

        public Boolean disable_expire;

        public Boolean independent_cache;

    }

    public static class ExperimentalOptions extends SingBoxOption {

        public ClashAPIOptions clash_api;

        public V2RayAPIOptions v2ray_api;

        public CacheFile cache_file;

        public DebugOptions debug;

    }

    public static class CacheFile extends SingBoxOption {

        public Boolean enabled;

        public Boolean store_fakeip;

        public String path;

        public String cache_id;


        // added from sing-box option.CacheFileOptions
        public String rdrc_timeout;

        public Boolean store_dns;

        public Boolean store_rdrc;

    }

    public static class HysteriaInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public String up;

        public Integer up_mbps;

        public String down;

        public Integer down_mbps;

        public String obfs;

        public List<HysteriaUser> users;

        public Long recv_window_conn;

        public Long recv_window_client;

        public Integer max_conn_client;

        public Boolean disable_mtu_discovery;

        public InboundTLSOptions tls;

    }

    public static class HysteriaUser extends SingBoxOption {

        public String name;

        // Generate note: Base64 String
        public String auth;

        public String auth_str;

    }

    public static class HysteriaOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String up;

        public Integer up_mbps;

        public String down;

        public Integer down_mbps;

        public String obfs;

        // Generate note: Base64 String
        public String auth;

        public String auth_str;

        public Long recv_window_conn;

        public Long recv_window;

        public Boolean disable_mtu_discovery;

        public String network;

        public OutboundTLSOptions tls;

        public List<String> server_ports;

        public String hop_interval;

    }

    public static class Hysteria2InboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public Integer up_mbps;

        public Integer down_mbps;

        public Hysteria2Obfs obfs;

        public List<Hysteria2User> users;

        public Boolean ignore_client_bandwidth;

        public InboundTLSOptions tls;

        public String masquerade;

    }

    public static class Hysteria2Obfs extends SingBoxOption {

        public String type;

        public String password;


        // added from sing-box option.Hysteria2Obfs
        public Integer max_packet_size;

        public Integer min_packet_size;

    }

    public static class Hysteria2User extends SingBoxOption {

        public String name;

        public String password;

    }

    public static class Hysteria2OutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public Integer up_mbps;

        public Integer down_mbps;

        public Hysteria2Obfs obfs;

        public String password;

        public String network;

        public OutboundTLSOptions tls;

        public List<String> server_ports;

        public String hop_interval;

    }


    public static class Inbound extends SingBoxOption {

        public String type;

        public String tag;

        // Generate note: option type:  public TunInboundOptions TunOptions;

        // Generate note: option type:  public RedirectInboundOptions RedirectOptions;

        // Generate note: option type:  public TProxyInboundOptions TProxyOptions;

        // Generate note: option type:  public DirectInboundOptions DirectOptions;

        // Generate note: option type:  public SocksInboundOptions SocksOptions;

        // Generate note: option type:  public HTTPMixedInboundOptions HTTPOptions;

        // Generate note: option type:  public HTTPMixedInboundOptions MixedOptions;

        // Generate note: option type:  public ShadowsocksInboundOptions ShadowsocksOptions;

        // Generate note: option type:  public VMessInboundOptions VMessOptions;

        // Generate note: option type:  public TrojanInboundOptions TrojanOptions;

        // Generate note: option type:  public NaiveInboundOptions NaiveOptions;

        // Generate note: option type:  public HysteriaInboundOptions HysteriaOptions;

        // Generate note: option type:  public ShadowTLSInboundOptions ShadowTLSOptions;

        // Generate note: option type:  public VLESSInboundOptions VLESSOptions;

        // Generate note: option type:  public TUICInboundOptions TUICOptions;

        // Generate note: option type:  public Hysteria2InboundOptions Hysteria2Options;

    }

    public static class InboundOptions extends SingBoxOption {

        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

    }

    public static class ListenOptions extends SingBoxOption {

        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

    }

    public static class NaiveInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<User> users;

        public String network;

        public InboundTLSOptions tls;

    }

    public static class NTPOptions extends SingBoxOption {

        public Boolean enabled;

        public String interval;

        public Boolean write_to_system;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;


        // added from sing-box option.NTPOptions
        public Boolean bind_address_no_port;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }


    public static class Outbound extends SingBoxOption {

        public String type;

        public String tag;

        // Generate note: option type:  public DirectOutboundOptions DirectOptions;

        // Generate note: option type:  public SocksOutboundOptions SocksOptions;

        // Generate note: option type:  public HTTPOutboundOptions HTTPOptions;

        // Generate note: option type:  public ShadowsocksOutboundOptions ShadowsocksOptions;

        // Generate note: option type:  public VMessOutboundOptions VMessOptions;

        // Generate note: option type:  public TrojanOutboundOptions TrojanOptions;

        // Generate note: option type:  public WireGuardOutboundOptions WireGuardOptions;

        // Generate note: option type:  public HysteriaOutboundOptions HysteriaOptions;

        // Generate note: option type:  public TorOutboundOptions TorOptions;

        // Generate note: option type:  public SSHOutboundOptions SSHOptions;

        // Generate note: option type:  public ShadowTLSOutboundOptions ShadowTLSOptions;

        // Generate note: option type:  public ShadowsocksROutboundOptions ShadowsocksROptions;

        // Generate note: option type:  public VLESSOutboundOptions VLESSOptions;

        // Generate note: option type:  public TUICOutboundOptions TUICOptions;

        // Generate note: option type:  public Hysteria2OutboundOptions Hysteria2Options;

        // Generate note: option type:  public SelectorOutboundOptions SelectorOptions;

        // Generate note: option type:  public URLTestOutboundOptions URLTestOptions;

    }

    public static class DialerOptions extends SingBoxOption {

        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

    }

    public static class ServerOptions extends SingBoxOption {

        public String server;

        public Integer server_port;

    }

    public static class MultiplexOptions extends SingBoxOption {

        public Boolean enabled;

        public String protocol;

        public Integer max_connections;

        public Integer min_streams;

        public Integer max_streams;

        public Boolean padding;


        // added from sing-box option.OutboundMultiplexOptions
        public Object brutal;

    }

    public static class OnDemandOptions extends SingBoxOption {

        public Boolean enabled;

        public List<OnDemandRule> rules;

    }

    public static class OnDemandRule extends SingBoxOption {

        public String action;

        // Generate note: Listable
        public List<String> dns_search_domain_match;

        // Generate note: Listable
        public List<String> dns_server_address_match;

        public String interface_type_match;

        // Generate note: Listable
        public List<String> ssid_match;

        public String probe_url;

    }


    public static class RedirectInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

    }

    public static class TProxyInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public String network;

    }

    public static class RouteOptions extends SingBoxOption {

        public List<Rule> rules;

        public List<RuleSet> rule_set;

        @SerializedName("final")
        public String final_;

        public Boolean find_process;

        public String default_domain_resolver;

        public Boolean auto_detect_interface;

        public Boolean override_android_vpn;

        public String default_interface;

        public Integer default_mark;


        // added from sing-box option.RouteOptions
        public String default_fallback_delay;

        public List<Object> default_fallback_network_type;

        public String default_http_client;

        public Object default_network_strategy;

        public List<Object> default_network_type;

        public List<String> dhcp_lease_files;

        public Boolean find_neighbor;

        public Object geoip;

        public Object geosite;

    }


    public static class Rule extends SingBoxOption {

        public String type;

        // Generate note: option type:  public DefaultRule DefaultOptions;

        // Generate note: option type:  public LogicalRule LogicalOptions;

    }

    public static class RuleSet extends SingBoxOption {

        public String type;

        public String tag;

        public String format;

        public String path;

        public String url;


        // added from sing-box option.RuleSet
        public String download_detour;

        public Object http_client;

        public String initial_path;

        public List<Object> rules;

        public String update_interval;

    }

    public static class DefaultRule extends SingBoxOption {

        // Generate note: Listable
        public List<String> inbound;

        public Integer ip_version;

        // Generate note: Listable
        public List<String> network;

        // Generate note: Listable
        public List<String> auth_user;

        // Generate note: Listable
        public List<String> protocol;

        // Generate note: Listable
        public List<String> domain;

        // Generate note: Listable
        public List<String> domain_suffix;

        // Generate note: Listable
        public List<String> domain_keyword;

        // Generate note: Listable
        public List<String> domain_regex;

        // Generate note: Listable
        public List<String> source_ip_cidr;

        // Generate note: Listable
        public List<String> ip_cidr;

        // Generate note: Listable
        public List<Integer> source_port;

        // Generate note: Listable
        public List<String> source_port_range;

        // Generate note: Listable
        public List<Integer> port;

        // Generate note: Listable
        public List<String> port_range;

        // Generate note: Listable
        public List<String> process_name;

        // Generate note: Listable
        public List<String> process_path;

        // Generate note: Listable
        public List<String> package_name;

        // Generate note: Listable
        public List<String> user;

        // Generate note: Listable
        public List<Integer> user_id;

        public String clash_mode;

        public Boolean invert;

        public String outbound;

    }


    public static class DNSRule extends SingBoxOption {

        public String type;

        // Generate note: option type:  public DefaultDNSRule DefaultOptions;

        // Generate note: option type:  public LogicalDNSRule LogicalOptions;

    }

    public static class DefaultDNSRule extends SingBoxOption {

        // Generate note: Listable
        public List<String> inbound;

        public Integer ip_version;

        // Generate note: Listable
        public List<String> query_type;

        // Generate note: Listable
        public List<String> network;

        // Generate note: Listable
        public List<String> auth_user;

        // Generate note: Listable
        public List<String> protocol;

        // Generate note: Listable
        public List<String> domain;

        // Generate note: Listable
        public List<String> domain_suffix;

        // Generate note: Listable
        public List<String> domain_keyword;

        // Generate note: Listable
        public List<String> domain_regex;

        // Generate note: Listable
        public List<String> geosite;

        // Generate note: Listable
        public List<String> source_ip_cidr;

        // Generate note: Listable
        public List<Integer> source_port;

        // Generate note: Listable
        public List<String> source_port_range;

        // Generate note: Listable
        public List<Integer> port;

        // Generate note: Listable
        public List<String> port_range;

        // Generate note: Listable
        public List<String> process_name;

        // Generate note: Listable
        public List<String> process_path;

        // Generate note: Listable
        public List<String> package_name;

        // Generate note: Listable
        public List<String> user;

        // Generate note: Listable
        public List<Integer> user_id;

        // Generate note: Listable
        public List<String> outbound;

        public String clash_mode;

        public Boolean invert;

        public String server;

        public Boolean disable_cache;

        public Integer rewrite_ttl;

    }

    public static class ShadowsocksInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public String network;

        public String method;

        public String password;

        public List<ShadowsocksUser> users;

        public List<ShadowsocksDestination> destinations;

    }

    public static class ShadowsocksUser extends SingBoxOption {

        public String name;

        public String password;

    }

    public static class ShadowsocksDestination extends SingBoxOption {

        public String name;

        public String password;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

    }

    public static class ShadowsocksOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String method;

        public String password;

        public String plugin;

        public String plugin_opts;

        public String network;

        public UDPOverTCPOptions udp_over_tcp;

        public MultiplexOptions multiplex;

    }

    public static class ShadowsocksROutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String method;

        public String password;

        public String obfs;

        public String obfs_param;

        public String protocol;

        public String protocol_param;

        public String network;

    }

    public static class ShadowTLSInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public Integer version;

        public String password;

        public List<ShadowTLSUser> users;

        public ShadowTLSHandshakeOptions handshake;

        public Map<String, ShadowTLSHandshakeOptions> handshake_for_server_name;

        public Boolean strict_mode;

    }

    public static class ShadowTLSUser extends SingBoxOption {

        public String name;

        public String password;

    }

    public static class ShadowTLSHandshakeOptions extends SingBoxOption {

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

    }

    public static class ShadowTLSOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public Integer version;

        public String password;

        public OutboundTLSOptions tls;

    }

    public static class SocksInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<User> users;

    }

    public static class HTTPMixedInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<User> users;

        public Boolean set_system_proxy;

        public InboundTLSOptions tls;

    }

    public static class SocksOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String version;

        public String username;

        public String password;

        public String network;

        public UDPOverTCPOptions udp_over_tcp;

    }

    public static class HTTPOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String username;

        public String password;

        public OutboundTLSOptions tls;

        public String path;

        public Map<String, String> headers;

    }

    public static class SSHOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String user;

        public String password;

        public String private_key;

        public String private_key_path;

        public String private_key_passphrase;

        // Generate note: Listable
        public List<String> host_key;

        // Generate note: Listable
        public List<String> host_key_algorithms;

        public String client_version;

    }

    public static class InboundTLSOptions extends SingBoxOption {

        public Boolean enabled;

        public String server_name;

        public Boolean insecure;

        // Generate note: Listable
        public List<String> alpn;

        public String min_version;

        public String max_version;

        // Generate note: Listable
        public List<String> cipher_suites;

        // Generate note: Listable
        public List<String> certificate;

        public String certificate_path;

        // Generate note: Listable
        public List<String> key;

        public String key_path;

        public InboundACMEOptions acme;

        public InboundECHOptions ech;

        public InboundRealityOptions reality;

    }

    public static class OutboundTLSOptions extends SingBoxOption {

        public Boolean enabled;

        public Boolean disable_sni;

        public String server_name;

        public Boolean insecure;

        // Generate note: Listable
        public List<String> alpn;

        public String min_version;

        public String max_version;

        // Generate note: Listable
        public List<String> cipher_suites;

        public String certificate;

        public String certificate_path;

        public OutboundECHOptions ech;

        public OutboundUTLSOptions utls;

        public OutboundRealityOptions reality;


        // added from sing-box option.OutboundTLSOptions
        public List<String> certificate_public_key_sha256;

        public List<String> client_certificate;

        public String client_certificate_path;

        public List<String> client_key;

        public String client_key_path;

        public List<Object> curve_preferences;

        public String engine;

        public Boolean fragment;

        public String fragment_fallback_delay;

        public String handshake_timeout;

        public Boolean kernel_rx;

        public Boolean kernel_tx;

        public Boolean record_fragment;

        public String spoof;

        public String spoof_method;

    }

    public static class InboundRealityOptions extends SingBoxOption {

        public Boolean enabled;

        public InboundRealityHandshakeOptions handshake;

        public String private_key;

        // Generate note: Listable
        public List<String> short_id;

        public Long max_time_difference;

    }

    public static class InboundRealityHandshakeOptions extends SingBoxOption {

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

    }

    public static class InboundECHOptions extends SingBoxOption {

        public Boolean enabled;

        // Generate note: Listable
        public List<String> key;

        public String key_path;

    }

    public static class OutboundECHOptions extends SingBoxOption {

        public Boolean enabled;

        // Generate note: Listable
        public List<String> config;

        public String config_path;


        // added from sing-box option.OutboundECHOptions
        public Boolean dynamic_record_sizing_disabled;

        public Boolean pq_signature_schemes_enabled;

        public String query_server_name;

    }

    public static class OutboundUTLSOptions extends SingBoxOption {

        public Boolean enabled;

        public String fingerprint;

    }

    public static class OutboundRealityOptions extends SingBoxOption {

        public Boolean enabled;

        public String public_key;

        public String short_id;

        // core fork (from sing-box-lx): "", "hybrid" or "classical"
        public String key_share;

        // core fork: the server's ML-DSA-65 public key, base64 (Xray mldsa65Verify)
        public String mldsa65_verify;

    }

    public static class InboundACMEOptions extends SingBoxOption {

        // Generate note: Listable
        public List<String> domain;

        public String data_directory;

        public String default_server_name;

        public String email;

        public String provider;

        public Boolean disable_http_challenge;

        public Boolean disable_tls_alpn_challenge;

        public Integer alternative_http_port;

        public Integer alternative_tls_port;

        public ACMEExternalAccountOptions external_account;

    }

    public static class ACMEExternalAccountOptions extends SingBoxOption {

        public String key_id;

        public String mac_key;

    }

    public static class TorOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        public String executable_path;

        public List<String> extra_args;

        public String data_directory;

        public Map<String, String> torrc;

    }

    public static class TrojanInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<TrojanUser> users;

        public InboundTLSOptions tls;

        public ServerOptions fallback;

        public Map<String, ServerOptions> fallback_for_alpn;

        public V2RayTransportOptions transport;

    }

    public static class TrojanUser extends SingBoxOption {

        public String name;

        public String password;

    }

    public static class TrojanOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String password;

        public String network;

        public OutboundTLSOptions tls;

        public MultiplexOptions multiplex;

        public V2RayTransportOptions transport;

    }

    public static class TUICInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<TUICUser> users;

        public String congestion_control;

        public Long auth_timeout;

        public Boolean zero_rtt_handshake;

        public String heartbeat;

        public InboundTLSOptions tls;

    }

    public static class TUICUser extends SingBoxOption {

        public String name;

        public String uuid;

        public String password;

    }

    public static class TUICOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String uuid;

        public String password;

        public String congestion_control;

        public String udp_relay_mode;

        public Boolean udp_over_stream;

        public Boolean zero_rtt_handshake;

        public String heartbeat;

        public String network;

        public OutboundTLSOptions tls;

    }

    public static class TunInboundOptions extends SingBoxOption {

        public String interface_name;

        public Integer mtu;

        // Generate note: Listable
        public List<String> inet4_address;

        // Generate note: Listable
        public List<String> inet6_address;

        public Boolean auto_route;

        public Boolean strict_route;

        // Generate note: Listable
        public List<String> inet4_route_address;

        // Generate note: Listable
        public List<String> inet6_route_address;

        // Generate note: Listable
        public List<String> include_interface;

        // Generate note: Listable
        public List<String> exclude_interface;

        // Generate note: Listable
        public List<Integer> include_uid;

        // Generate note: Listable
        public List<String> include_uid_range;

        // Generate note: Listable
        public List<Integer> exclude_uid;

        // Generate note: Listable
        public List<String> exclude_uid_range;

        // Generate note: Listable
        public List<Integer> include_android_user;

        // Generate note: Listable
        public List<String> include_package;

        // Generate note: Listable
        public List<String> exclude_package;

        public Boolean endpoint_independent_nat;

        public String udp_timeout;

        public String stack;

        public TunPlatformOptions platform;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

    }

    public static class TunPlatformOptions extends SingBoxOption {

        public HTTPProxyOptions http_proxy;

    }

    public static class HTTPProxyOptions extends SingBoxOption {

        public Boolean enabled;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;


        // added from sing-box option.HTTPProxyOptions
        public List<String> bypass_domain;

        public List<String> match_domain;

    }


    public static class UDPOverTCPOptions extends SingBoxOption {

        public Boolean enabled;

        public Integer version;

    }

    public static class V2RayAPIOptions extends SingBoxOption {

        public String listen;

        public V2RayStatsServiceOptions stats;

    }

    public static class V2RayStatsServiceOptions extends SingBoxOption {

        public Boolean enabled;

        public List<String> inbounds;

        public List<String> outbounds;

        public List<String> users;

    }


    public static class V2RayTransportOptions extends SingBoxOption {

        public String type;

        // Generate note: option type:  public V2RayHTTPOptions HTTPOptions;

        // Generate note: option type:  public V2RayWebsocketOptions WebsocketOptions;

        // Generate note: option type:  public V2RayQUICOptions QUICOptions;

        // Generate note: option type:  public V2RayGRPCOptions GRPCOptions;

    }

    public static class V2RayHTTPOptions extends SingBoxOption {

        // Generate note: Listable
        public List<String> host;

        public String path;

        public String method;

        public Map<String, String> headers;

        public String idle_timeout;

        public String ping_timeout;

    }

    public static class V2RayWebsocketOptions extends SingBoxOption {

        public String path;

        public Map<String, String> headers;

        public Integer max_early_data;

        public String early_data_header_name;

    }


    public static class V2RayGRPCOptions extends SingBoxOption {

        public String service_name;

        public String idle_timeout;

        public String ping_timeout;

        public Boolean permit_without_stream;

        // Generate note: option type:  public Boolean ForceLite;

    }

    public static class V2RayHTTPUpgradeOptions extends SingBoxOption {

        public String host;

        public String path;

        public Map<String, String> headers;

    }

    public static class VLESSInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<VLESSUser> users;

        public InboundTLSOptions tls;

        public V2RayTransportOptions transport;

    }

    public static class VLESSUser extends SingBoxOption {

        public String name;

        public String uuid;

        public String flow;

    }

    public static class VLESSOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String uuid;

        public String flow;

        public String network;

        public OutboundTLSOptions tls;

        public MultiplexOptions multiplex;

        public V2RayTransportOptions transport;

        public String packet_encoding;

    }

    public static class VMessInboundOptions extends SingBoxOption {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<VMessUser> users;

        public InboundTLSOptions tls;

        public V2RayTransportOptions transport;

    }

    public static class VMessUser extends SingBoxOption {

        public String name;

        public String uuid;

        public Integer alterId;

    }

    public static class VMessOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String uuid;

        public String security;

        public Integer alter_id;

        public Boolean global_padding;

        public Boolean authenticated_length;

        public String network;

        public OutboundTLSOptions tls;

        public String packet_encoding;

        public MultiplexOptions multiplex;

        public V2RayTransportOptions transport;

    }

    public static class WireGuardOutboundOptions extends SingBoxOption {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        // Generate note: option type:  public Boolean UDPFragmentDefault;

        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        public Boolean system_interface;

        public String interface_name;

        // Generate note: Listable
        public List<String> local_address;

        public String private_key;

        public List<WireGuardPeer> peers;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String peer_public_key;

        public String pre_shared_key;

        // Generate note: Base64 String
        public String reserved;

        public Integer workers;

        public Integer mtu;

        public String network;

    }

    public static class WireGuardPeer extends SingBoxOption {

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String public_key;

        public String pre_shared_key;

        // Generate note: Listable
        public List<String> allowed_ips;

        // Generate note: Base64 String
        public String reserved;

    }

    public static class Inbound_TunOptions extends Inbound {

        public String interface_name;

        public Integer mtu;

        // Generate note: Listable
        public List<String> address;

        // Generate note: Listable
        public List<String> inet4_address;

        // Generate note: Listable
        public List<String> inet6_address;

        public Boolean auto_route;

        public Boolean strict_route;

        // Generate note: Listable
        public List<String> inet4_route_address;

        // Generate note: Listable
        public List<String> inet6_route_address;

        // Generate note: Listable
        public List<String> include_interface;

        // Generate note: Listable
        public List<String> exclude_interface;

        // Generate note: Listable
        public List<Integer> include_uid;

        // Generate note: Listable
        public List<String> include_uid_range;

        // Generate note: Listable
        public List<Integer> exclude_uid;

        // Generate note: Listable
        public List<String> exclude_uid_range;

        // Generate note: Listable
        public List<Integer> include_android_user;

        // Generate note: Listable
        public List<String> include_package;

        // Generate note: Listable
        public List<String> exclude_package;

        public Boolean endpoint_independent_nat;

        public String udp_timeout;

        public String stack;

        public TunPlatformOptions platform;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;


        // added from sing-box option.TunInboundOptions
        public Boolean auto_redirect;

        public Object auto_redirect_input_mark;

        public Integer auto_redirect_iproute2_fallback_rule_index;

        public Integer auto_redirect_nfqueue;

        public Object auto_redirect_output_mark;

        public Object auto_redirect_reset_mark;

        public List<Object> dns_address;

        public String dns_mode;

        public List<String> exclude_mac_address;

        public Boolean exclude_mptcp;

        public Boolean gso;

        public List<String> include_mac_address;

        public List<Object> inet4_route_exclude_address;

        public List<Object> inet6_route_exclude_address;

        public Integer iproute2_rule_index;

        public Integer iproute2_table_index;

        public List<Object> loopback_address;

        public String netns;

        public List<Object> route_address;

        public List<String> route_address_set;

        public List<Object> route_exclude_address;

        public List<String> route_exclude_address_set;

        public Boolean udp_disable_domain_unmapping;

        public Object udp_filtering;

        public Object udp_mapping;

        public Integer udp_nat_max;

    }

    public static class Inbound_RedirectOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;


        // added from sing-box option.RedirectInboundOptions
        public String bind_interface;

        public Boolean disable_tcp_keep_alive;

        public String netns;

        public Boolean reuse_addr;

        public Object routing_mark;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_disable_domain_unmapping;

    }

    public static class Inbound_TProxyOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public String network;


        // added from sing-box option.TProxyInboundOptions
        public String bind_interface;

        public Boolean disable_tcp_keep_alive;

        public String netns;

        public Boolean reuse_addr;

        public Object routing_mark;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_disable_domain_unmapping;

        public Object udp_filtering;

        public Object udp_mapping;

        public Integer udp_nat_max;

    }

    public static class Inbound_DirectOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public String network;

        public String override_address;

        public Integer override_port;


        // added from sing-box option.DirectInboundOptions
        public String bind_interface;

        public Boolean disable_tcp_keep_alive;

        public String netns;

        public Boolean reuse_addr;

        public Object routing_mark;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_disable_domain_unmapping;

    }

    public static class Inbound_SocksOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<User> users;


        // added from sing-box option.SocksInboundOptions
        public String bind_interface;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public String netns;

        public Boolean reuse_addr;

        public Object routing_mark;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_disable_domain_unmapping;

    }

    public static class Inbound_HTTPOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<User> users;

        public Boolean set_system_proxy;

        public InboundTLSOptions tls;


        // added from sing-box option.HTTPMixedInboundOptions
        public String bind_interface;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public String netns;

        public Boolean reuse_addr;

        public Object routing_mark;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_disable_domain_unmapping;

    }

    public static class Inbound_MixedOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<User> users;

        public Boolean set_system_proxy;

        public InboundTLSOptions tls;


        // added from sing-box option.HTTPMixedInboundOptions
        public String bind_interface;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public String netns;

        public Boolean reuse_addr;

        public Object routing_mark;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_disable_domain_unmapping;

    }

    public static class Inbound_ShadowsocksOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public String network;

        public String method;

        public String password;

        public List<ShadowsocksUser> users;

        public List<ShadowsocksDestination> destinations;

    }

    public static class Inbound_VMessOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<VMessUser> users;

        public InboundTLSOptions tls;

        public V2RayTransportOptions transport;

    }

    public static class Inbound_TrojanOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<TrojanUser> users;

        public InboundTLSOptions tls;

        public ServerOptions fallback;

        public Map<String, ServerOptions> fallback_for_alpn;

        public V2RayTransportOptions transport;

    }

    public static class Inbound_NaiveOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<User> users;

        public String network;

        public InboundTLSOptions tls;

    }

    public static class Inbound_HysteriaOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public String up;

        public Integer up_mbps;

        public String down;

        public Integer down_mbps;

        public String obfs;

        public List<HysteriaUser> users;

        public Long recv_window_conn;

        public Long recv_window_client;

        public Integer max_conn_client;

        public Boolean disable_mtu_discovery;

        public InboundTLSOptions tls;

    }

    public static class Inbound_ShadowTLSOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public Integer version;

        public String password;

        public List<ShadowTLSUser> users;

        public ShadowTLSHandshakeOptions handshake;

        public Map<String, ShadowTLSHandshakeOptions> handshake_for_server_name;

        public Boolean strict_mode;

    }

    public static class Inbound_VLESSOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<VLESSUser> users;

        public InboundTLSOptions tls;

        public V2RayTransportOptions transport;

    }

    public static class Inbound_TUICOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public List<TUICUser> users;

        public String congestion_control;

        public Long auth_timeout;

        public Boolean zero_rtt_handshake;

        public String heartbeat;

        public InboundTLSOptions tls;

    }

    public static class Inbound_Hysteria2Options extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        public String detour;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        // End of public InboundOptions ;

        // End of public ListenOptions ;

        public Integer up_mbps;

        public Integer down_mbps;

        public Hysteria2Obfs obfs;

        public List<Hysteria2User> users;

        public Boolean ignore_client_bandwidth;

        public InboundTLSOptions tls;

        public String masquerade;

    }

    public static class Outbound_DirectOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        public String override_address;

        public Integer override_port;

        public Integer proxy_protocol;


        // added from sing-box option.DirectOutboundOptions
        public Boolean bind_address_no_port;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_SocksOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String version;

        public String username;

        public String password;

        public String network;

        public UDPOverTCPOptions udp_over_tcp;


        // added from sing-box option.SOCKSOutboundOptions
        public Boolean bind_address_no_port;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_HTTPOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String username;

        public String password;

        public OutboundTLSOptions tls;

        public String path;

        public Map<String, String> headers;


        // added from sing-box option.HTTPOutboundOptions
        public Boolean bind_address_no_port;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_ShadowsocksOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String method;

        public String password;

        public String plugin;

        public String plugin_opts;

        public String network;

        public UDPOverTCPOptions udp_over_tcp;

        public MultiplexOptions multiplex;


        // added from sing-box option.ShadowsocksOutboundOptions
        public Boolean bind_address_no_port;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_VMessOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String uuid;

        public String security;

        public Integer alter_id;

        public Boolean global_padding;

        public Boolean authenticated_length;

        public String network;

        public OutboundTLSOptions tls;

        public String packet_encoding;

        public MultiplexOptions multiplex;

        public V2RayTransportOptions transport;


        // added from sing-box option.VMessOutboundOptions
        public Boolean bind_address_no_port;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_TrojanOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String password;

        public String network;

        public OutboundTLSOptions tls;

        public MultiplexOptions multiplex;

        public V2RayTransportOptions transport;


        // added from sing-box option.TrojanOutboundOptions
        public Boolean bind_address_no_port;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_WireGuardOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        public Boolean system_interface;

        public String interface_name;

        // Generate note: Listable
        public List<String> local_address;

        public String private_key;

        public List<WireGuardPeer> peers;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String peer_public_key;

        public String pre_shared_key;

        // Generate note: Base64 String
        public String reserved;

        public Integer workers;

        public Integer mtu;

        public String network;

    }

    public static class Endpoint_WireGuardOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String protect_path;

        public Boolean tcp_fast_open;

        public String domain_strategy;

        public Boolean system;

        public String name;

        public List<String> address;

        public String private_key;

        public List<EndpointWireGuardPeer> peers;

        public Integer workers;

        public Integer mtu;


        // added from sing-box option.WireGuardEndpointOptions
        public Boolean bind_address_no_port;

        public String connect_timeout;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public String fallback_delay;

        public List<Object> fallback_network_type;

        public Object inet4_bind_address;

        public Object inet6_bind_address;

        public Integer listen_port;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public Boolean reuse_addr;

        public Object routing_mark;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean tcp_multi_path;

        public Object udp_filtering;

        public Boolean udp_fragment;

        public Object udp_mapping;

        public Integer udp_nat_max;

        public String udp_timeout;

    }

    public static class EndpointWireGuardPeer extends SingBoxOption {

        public String address;

        public Integer port;

        public String public_key;

        public String pre_shared_key;

        public List<String> allowed_ips;

        // Generate note: Base64 String
        public String reserved;


        // added from sing-box option.WireGuardPeer
        public Integer persistent_keepalive_interval;

    }

    public static class Outbound_HysteriaOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String up;

        public Integer up_mbps;

        public String down;

        public Integer down_mbps;

        public String obfs;

        // Generate note: Base64 String
        public String auth;

        public String auth_str;

        public Long recv_window_conn;

        public Long recv_window;

        public Boolean disable_mtu_discovery;

        public String network;

        public OutboundTLSOptions tls;

        public List<String> server_ports;

        public String hop_interval;


        // added from sing-box option.HysteriaOutboundOptions
        public Boolean bind_address_no_port;

        public Object connection_receive_window;

        public Boolean disable_path_mtu_discovery;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String idle_timeout;

        public Integer initial_packet_size;

        public String keep_alive_period;

        public Integer max_concurrent_streams;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public Object stream_receive_window;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_TorOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        public String executable_path;

        public List<String> extra_args;

        public String data_directory;

        public Map<String, String> torrc;


        // added from sing-box option.TorOutboundOptions
        public Boolean bind_address_no_port;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_SSHOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String user;

        public String password;

        public String private_key;

        public String private_key_path;

        public String private_key_passphrase;

        // Generate note: Listable
        public List<String> host_key;

        // Generate note: Listable
        public List<String> host_key_algorithms;

        public String client_version;


        // added from sing-box option.SSHOutboundOptions
        public Boolean bind_address_no_port;

        public List<String> cipher;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public List<String> kex_algorithm;

        public List<String> mac;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_ShadowTLSOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public Integer version;

        public String password;

        public OutboundTLSOptions tls;


        // added from sing-box option.ShadowTLSOutboundOptions
        public Boolean bind_address_no_port;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_ShadowsocksROptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String method;

        public String password;

        public String obfs;

        public String obfs_param;

        public String protocol;

        public String protocol_param;

        public String network;

    }

    public static class Outbound_VLESSOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String uuid;

        public String flow;

        public String encryption;

        public String network;

        public OutboundTLSOptions tls;

        public MultiplexOptions multiplex;

        public V2RayTransportOptions transport;

        public String packet_encoding;


        // added from sing-box option.VLESSOutboundOptions
        public Boolean bind_address_no_port;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_TUICOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public String uuid;

        public String password;

        public String congestion_control;

        public String udp_relay_mode;

        public Boolean udp_over_stream;

        public Boolean zero_rtt_handshake;

        public String heartbeat;

        public String network;

        public OutboundTLSOptions tls;


        // added from sing-box option.TUICOutboundOptions
        public Boolean bind_address_no_port;

        public Object connection_receive_window;

        public Boolean disable_path_mtu_discovery;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String idle_timeout;

        public Integer initial_packet_size;

        public String keep_alive_period;

        public Integer max_concurrent_streams;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public Object stream_receive_window;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_Hysteria2Options extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;


        public String domain_strategy;

        public String fallback_delay;

        // End of public DialerOptions ;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // End of public ServerOptions ;

        public Integer up_mbps;

        public Integer down_mbps;

        public Hysteria2Obfs obfs;

        public String password;

        public String network;

        public OutboundTLSOptions tls;

        public List<String> server_ports;

        public String hop_interval;


        // added from sing-box option.Hysteria2OutboundOptions
        public String bbr_profile;

        public Boolean bind_address_no_port;

        public Boolean brutal_debug;

        public Object connection_receive_window;

        public Boolean disable_chrome_parrot;

        public Boolean disable_path_mtu_discovery;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public List<Object> fallback_network_type;

        public String hop_interval_max;

        public String idle_timeout;

        public Integer initial_packet_size;

        public String keep_alive_period;

        public Integer max_concurrent_streams;

        public String netns;

        public Object network_strategy;

        public List<Object> network_type;

        public Object realm;

        public Object stream_receive_window;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

    public static class Outbound_SelectorOptions extends Outbound {

        public List<String> outbounds;

        @SerializedName("default")
        public String default_;


        // added from sing-box option.SelectorOutboundOptions
        public Boolean interrupt_exist_connections;

    }

    public static class Outbound_URLTestOptions extends Outbound {

        public List<String> outbounds;

        public String url;

        public String interval;

        public Integer tolerance;


        // added from sing-box option.URLTestOutboundOptions
        public String idle_timeout;

        public Boolean interrupt_exist_connections;

    }

    public static class Rule_DefaultOptions extends Rule {

        // Generate note: Listable
        public List<String> inbound;

        public Integer ip_version;

        // Generate note: Listable
        public List<String> network;

        // Generate note: Listable
        public List<String> auth_user;

        // Generate note: Listable
        public List<String> protocol;

        // Generate note: Listable
        public List<String> domain;

        // Generate note: Listable
        public List<String> domain_suffix;

        // Generate note: Listable
        public List<String> domain_keyword;

        // Generate note: Listable
        public List<String> domain_regex;

        public List<String> rule_set;

        public Boolean source_ip_is_private;

        public Boolean ip_is_private;

        // Generate note: Listable
        public List<String> source_ip_cidr;

        // Generate note: Listable
        public List<String> ip_cidr;

        // Generate note: Listable
        public List<Integer> source_port;

        // Generate note: Listable
        public List<String> source_port_range;

        // Generate note: Listable
        public List<Integer> port;

        // Generate note: Listable
        public List<String> port_range;

        // Generate note: Listable
        public List<String> process_name;

        // Generate note: Listable
        public List<String> process_path;

        // Generate note: Listable
        public List<String> package_name;

        // Generate note: Listable
        public List<String> user;

        // Generate note: Listable
        public List<Integer> user_id;

        public String clash_mode;

        public Boolean invert;

        public String action;

        public String outbound;

        public String strategy;


        // added from sing-box option.DefaultRule
        public List<String> client;

        public Object client_subnet;

        public List<Object> default_interface_address;

        public Boolean disable_cache;

        public Boolean disable_optimistic_cache;

        public Integer fallback_delay;

        public List<String> geoip;

        public List<String> geosite;

        public Object interface_address;

        public String method;

        public Object network_interface_address;

        public Boolean network_is_constrained;

        public Boolean network_is_expensive;

        public Object network_strategy;

        public List<Object> network_type;

        public Boolean no_drop;

        public String override_address;

        public Integer override_port;

        public List<String> package_name_regex;

        public List<String> preferred_by;

        public List<String> process_path_regex;

        public Integer rewrite_ttl;

        public Boolean rule_set_ip_cidr_match_source;

        public Boolean rule_set_ipcidr_match_source;

        public String server;

        public List<String> sniffer;

        public List<String> source_geoip;

        public List<String> source_hostname;

        public List<String> source_mac_address;

        public String timeout;

        public Boolean tls_fragment;

        public String tls_fragment_fallback_delay;

        public Boolean tls_record_fragment;

        public String tls_spoof;

        public String tls_spoof_method;

        public Boolean udp_connect;

        public Boolean udp_disable_domain_unmapping;

        public String udp_timeout;

        public List<String> wifi_bssid;

        public List<String> wifi_ssid;

    }

    public static class DNSRule_DefaultOptions extends DNSRule {

        // Generate note: Listable
        public List<String> inbound;

        public Integer ip_version;

        // Generate note: Listable
        public List<String> query_type;

        // Generate note: Listable
        public List<String> network;

        // Generate note: Listable
        public List<String> auth_user;

        // Generate note: Listable
        public List<String> protocol;

        // Generate note: Listable
        public List<String> domain;

        // Generate note: Listable
        public List<String> domain_suffix;

        // Generate note: Listable
        public List<String> domain_keyword;

        // Generate note: Listable
        public List<String> domain_regex;

        public List<String> rule_set;

        // Generate note: Listable
        public List<String> source_ip_cidr;

        // Generate note: Listable
        public List<Integer> source_port;

        // Generate note: Listable
        public List<String> source_port_range;

        // Generate note: Listable
        public List<Integer> port;

        // Generate note: Listable
        public List<String> port_range;

        // Generate note: Listable
        public List<String> process_name;

        // Generate note: Listable
        public List<String> process_path;

        // Generate note: Listable
        public List<String> package_name;

        // Generate note: Listable
        public List<String> user;

        // Generate note: Listable
        public List<Integer> user_id;

        // Generate note: Listable
        public List<String> outbound;

        public String clash_mode;

        public Boolean invert;

        public String action;

        public String rcode;

        public String strategy;

        public String server;

        public Boolean disable_cache;

        public Integer rewrite_ttl;


        // added from sing-box option.DefaultDNSRule
        public List<Object> answer;

        public Object client_subnet;

        public List<Object> default_interface_address;

        public Boolean disable_optimistic_cache;

        public List<Object> extra;

        public List<String> geoip;

        public List<String> geosite;

        public Object interface_address;

        public Boolean ip_accept_any;

        public List<String> ip_cidr;

        public Boolean ip_is_private;

        public Object match_response;

        public String method;

        public Object network_interface_address;

        public Boolean network_is_constrained;

        public Boolean network_is_expensive;

        public List<Object> network_type;

        public Boolean no_drop;

        public List<Object> ns;

        public List<String> package_name_regex;

        public List<String> preferred_by;

        public List<String> process_path_regex;

        public List<Object> query_client_subnet;

        public Boolean query_dnssec;

        public Boolean race;

        public Boolean remove_client_subnet;

        public List<Object> response_answer;

        public List<Object> response_extra;

        public List<Object> response_ns;

        public Object response_rcode;

        public Boolean rule_set_ip_cidr_accept_empty;

        public Boolean rule_set_ip_cidr_match_source;

        public Boolean rule_set_ipcidr_match_source;

        public List<String> source_geoip;

        public List<String> source_hostname;

        public Boolean source_ip_is_private;

        public List<String> source_mac_address;

        public Boolean speculative;

        public String tag;

        public String timeout;

        public List<String> wifi_bssid;

        public List<String> wifi_ssid;

    }

    public static class V2RayTransportOptions_HTTPOptions extends V2RayTransportOptions {

        // Generate note: Listable
        public List<String> host;

        public String path;

        public String method;

        public Map<String, String> headers;

        public String idle_timeout;

        public String ping_timeout;

    }

    public static class V2RayTransportOptions_WebsocketOptions extends V2RayTransportOptions {

        public String path;

        public Map<String, String> headers;

        public Integer max_early_data;

        public String early_data_header_name;

    }


    public static class V2RayTransportOptions_GRPCOptions extends V2RayTransportOptions {

        public String service_name;

        public String idle_timeout;

        public String ping_timeout;

        public Boolean permit_without_stream;


    }

    public static class V2RayTransportOptions_HTTPUpgradeOptions extends V2RayTransportOptions {

        public String host;

        public String path;



        // added from sing-box option.V2RayHTTPUpgradeOptions
        public Map<String, Object> headers;

    }

    // sing-box Options 生成器已经坏了，以下是从 husi 抄的

    public static class Outbound_AnyTLSOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        public String domain_strategy;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

        public String password;

        public String idle_session_check_interval;

        public String idle_session_timeout;


        // added from sing-box option.AnyTLSOutboundOptions
        public Boolean bind_address_no_port;

        public String client_metadata;

        public Boolean disable_tcp_keep_alive;

        public Object domain_resolver;

        public Integer min_idle_session;

        public String netns;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

    }

}
