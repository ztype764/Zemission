import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.UUID;

public class TestNet {
    public static void main(String[] args) {
        System.out.println("Starting TestNet...");
        try {
            System.out.println("Testing UUID (triggers SecureRandom)...");
            long start = System.currentTimeMillis();
            UUID uuid = UUID.randomUUID();
            System.out.println("UUID generated: " + uuid + " in " + (System.currentTimeMillis() - start) + "ms");

            System.out.println("Testing NetworkInterface.getNetworkInterfaces()...");
            start = System.currentTimeMillis();
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            int count = 0;
            if (nets != null) {
                while (nets.hasMoreElements()) {
                    NetworkInterface net = nets.nextElement();
                    System.out.println("Found interface: " + net.getName() + " - " + net.getDisplayName());
                    count++;
                }
            }
            System.out.println("Found " + count + " interfaces in " + (System.currentTimeMillis() - start) + "ms");

        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.out.println("TestNet Finished.");
    }
}
