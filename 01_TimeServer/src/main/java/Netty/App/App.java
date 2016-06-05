package Netty.App;


/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws Exception
    {
    	// 测试地址 http://127.0.0.1:10000/
		int port = 10000;
		if (args != null && args.length > 0) {
			try {
				port = Integer.valueOf(args[0]);
			} catch (NumberFormatException e){
				// 采用默认值
			}
		}
		new TimerServer().bind(port);
    }
}
