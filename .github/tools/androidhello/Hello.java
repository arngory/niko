// Hello: one HTTPS request from OkHttp (the version the app ships) over Android's own TLS (Conscrypt), as an app
// on the phone makes it, run on the emulator by app_process (browser-hellos.yml). The name is sent as the server name;
// the connection goes to the address given, the runner's capture (hello.py) behind the emulator's 10.0.2.2.
//
//	CLASSPATH=hello.dex app_process /system/bin Hello NAME ADDRESS PORT
import java.net.InetAddress;
import java.util.Collections;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Hello {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: Hello NAME ADDRESS PORT");
            System.exit(2);
        }
        final InetAddress address = InetAddress.getByName(args[1]);
        OkHttpClient client = new OkHttpClient.Builder()
                .dns(hostname -> Collections.singletonList(address))
                .build();
        Request request = new Request.Builder().url("https://" + args[0] + ":" + args[2] + "/").build();
        try (Response response = client.newCall(request).execute()) {
            System.out.println("answer: " + response.code());
        } catch (Exception e) {
            // the capture closes the connection after the ClientHello: a failure here is the expected end
            System.out.println("ended: " + e);
        }
        System.exit(0);
    }
}
