package Netty.App;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws Exception
    {
    	int port = 10000;
    	if (args != null && args.length > 0) {
    		try {
    			port = Integer.valueOf(args[0]);
    			
    		} catch (NumberFormatException e) {
    			// 采用默认值
    		}
    	}
    	new TimeClient().connect(port, "127.0.0.1");
    }
}
