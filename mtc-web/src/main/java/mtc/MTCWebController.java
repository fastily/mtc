package mtc;

import java.util.HashMap;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import com.github.scribejava.core.model.OAuth1AccessToken;

import fastily.jwiki.core.Wiki;
import fastily.mwoauth.MWOAuth;

/**
 * Simple controller for MTC-web.
 * 
 * @author Fastily
 *
 */
@RestController
public class MTCWebController
{
	/**
	 * Static shared MWOAuth object
	 */
	private MWOAuth o = new MWOAuth(System.getProperty("consumerID"), System.getProperty("clientSecret"), "en.wikipedia.org");
	
	/**
	 * The MTC Object to use
	 */
	private MTC mtc = new MTC(new Wiki(MStrings.wpHN), new Wiki(MStrings.comHN));
	
	/**
	 * End point which redirects user to OAuth if there is no existing session.
	 * 
	 * @return A ModelAndView redirecting the user's browser to the authorization URL.
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public ModelAndView establishSession()
	{
		try
		{
			return new ModelAndView("redirect:" + o.getAuthorizationURL());
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}

		return null;
	}
	
	@RequestMapping(value = "/genDesc", method = RequestMethod.GET, params = { "title" })
	public HashMap<String, String> generateDescPage(@RequestParam(value = "title") String title)
	{

			return null; //TODO: fixme
		
	}

	@RequestMapping(value = "/", method = RequestMethod.GET, params = { "oauth_verifier", "oauth_token" })
	public OAuthPair getA(@RequestParam(value = "oauth_verifier") String oauth_verifier,
			@RequestParam(value = "oauth_token") String oauth_token)
	{

		System.err.println("in here!");
		try
		{
			OAuth1AccessToken accessToken = o.getAccessToken(oauth_verifier);

			return new OAuthPair(accessToken.getToken(), accessToken.getTokenSecret());
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}

		System.out.println("Returning null!");
		return null;
	}

	@RequestMapping(value = "/hello", produces = "text/plain")
	public String hello()
	{
		return "Hello, World!";
	}

	public static class OAuthPair
	{
		public final String token, secret;

		public OAuthPair(String token, String secret)
		{
			this.token = token;
			this.secret = secret;
		}
	}
}