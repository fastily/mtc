package mtc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for MTCWeb.
 * 
 * @author Fastily
 *
 */
@SpringBootApplication
public class MTCWebApp
{
	/**
	 * Main driver
	 * 
	 * @param args Program arguments
	 */
	public static void main(String[] args)
	{
		SpringApplication.run(MTCWebApp.class, args);
	}
}