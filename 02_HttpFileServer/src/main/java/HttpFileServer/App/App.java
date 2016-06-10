package HttpFileServer.App;

/**
 * Hello world!
 *
 */
public class App {
	public static void main(String[] args) throws Exception {
		int port = 10000;
		if (args.length > 0) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}

		String url = HttpFileServer.DEFAULT_URL;
		if (args.length > 1) {
			url = args[1];
			
		
		}
		
		new HttpFileServer().run(port, url);
	}
}
