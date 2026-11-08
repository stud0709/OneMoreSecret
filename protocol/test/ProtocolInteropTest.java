import org.junit.Test;
import java.nio.file.Path;
public class ProtocolInteropTest {
    @Test public void sharedProtocolFixturesAndNegativeCases() throws Exception {
        ProtocolTest.run(Path.of(System.getProperty("oms.fixtures", "../protocol/test-vectors/fixtures.json")));
    }
}
