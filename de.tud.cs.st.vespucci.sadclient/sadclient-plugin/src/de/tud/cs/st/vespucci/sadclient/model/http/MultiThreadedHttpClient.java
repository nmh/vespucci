package de.tud.cs.st.vespucci.sadclient.model.http;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.protocol.BasicHttpContext;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.params.AuthPNames;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.eclipse.core.runtime.IProgressMonitor;

import de.tud.cs.st.vespucci.sadclient.Activator;

/**
 * Thread-safe wrapper around the {@link DefaultHttpClient} using a
 * {@link ThreadSafeClientConnManager} and enables progress. The HTTP-methods
 * expect successful termination of the client call and will throw an
 * RequestException when the server responds non-okay.
 * 
 * TODO The client is not multithreaded anymore so rename the whole thing when a
 * valid solution found
 * 
 * @author Mateusz Parzonka
 * 
 */
public class MultiThreadedHttpClient {

    private final DefaultHttpClient client;
    private final static String DEFAULT_CHARSET = "UTF-8";
    private final IdleConnectionMonitor idleConnectionMonitor;
    private HttpContext context;

    /**
     * The client will use no authentication.
     */
    public MultiThreadedHttpClient() {
	super();
	context = new BasicHttpContext();
	SchemeRegistry schemeRegistry = new SchemeRegistry();
	schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
	// standard client params
	HttpParams params = new BasicHttpParams();
	params.setParameter(CoreProtocolPNames.HTTP_ELEMENT_CHARSET, DEFAULT_CHARSET);
	params.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, DEFAULT_CHARSET);
	params.setParameter(CoreProtocolPNames.USER_AGENT, Activator.PLUGIN_ID);
	params.setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true); // prevents
									   // premature
									   // body
									   // transmits
	params.setParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false);
	params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 10000); // we
									     // try
									     // to
									     // connect
									     // for
									     // 10
									     // sec
	List<Header> defaultHeaders = new ArrayList<Header>();
	defaultHeaders.add(new BasicHeader("accept", "application/xml"));
	params.setParameter(ClientPNames.DEFAULT_HEADERS, defaultHeaders);

	ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(schemeRegistry);
	cm.setMaxTotal(20);
	cm.setDefaultMaxPerRoute(20);

	client = new DefaultHttpClient(cm, params);

	idleConnectionMonitor = new IdleConnectionMonitor(cm);
	idleConnectionMonitor.setDaemon(true);
	idleConnectionMonitor.start();
    }

    /**
     * The client will use Http Digest Authentication with the provided
     * credentials.
     * 
     * @param userName
     * @param password
     */
    public MultiThreadedHttpClient(String userName, String password) {
	this();
	CredentialsProvider credsProvider = new BasicCredentialsProvider();
	client.setCredentialsProvider(credsProvider);
	credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));
	List<String> authPrefs = new ArrayList<String>(1);
	authPrefs.add(AuthPolicy.DIGEST);
	client.getParams().setParameter(AuthPNames.TARGET_AUTH_PREF, authPrefs);
    }

    public HttpResponse get(String url) throws RequestException {
	HttpResponse response = null;
	try {
	    final HttpGet get = new HttpGet(url);
	    response = executeWithContext(get);
	} catch (Exception e) {
	    throw new HttpClientException(e);
	}
	expectStatusCode(response, 200);
	return response;
    }

    public HttpResponse get(String url, String acceptedContentType) throws RequestException {
	HttpResponse response = null;
	try {
	    final HttpGet get = new HttpGet(url);
	    get.setHeader("accept", acceptedContentType);
	    response = executeWithContext(get);
	} catch (Exception e) {
	    throw new HttpClientException(e);
	}
	expectStatusCode(response, 200);
	return response;
    }

    public HttpResponse get(String url, String acceptedContentType, IProgressMonitor progressMonitor)
	    throws RequestException {
	HttpResponse response = null;
	final HttpGet get = new HttpGet(url);
	get.setHeader("accept", acceptedContentType);
	try {
	    response = executeWithContext(get);
	    // HttpEntityWithProgress.attachProgressMonitor(response,
	    // progressMonitor);
	} catch (Exception e) {
	    throw new RequestException(e);
	}
	expectStatusCode(response, 200);
	return response;
    }

    public HttpResponse put(String url, String string, String mimeType) throws RequestException {
	try {
	    return put(url, new StringEntity(string, mimeType, DEFAULT_CHARSET));
	} catch (UnsupportedEncodingException e) {
	    throw new RequestException(e);
	}
    }

    public HttpResponse put(String url, File file, String mimeType) throws RequestException {
	return put(url, new FileEntity(file, mimeType));
    }

    public HttpResponse putAsMultipart(String url, String fieldName, File file, String mimeType,
	    IProgressMonitor progressMonitor) throws RequestException {
	MultipartEntity multipartEntity = new MultipartEntityWithProgress(progressMonitor);
	ContentBody contentBody = new FileBody(file, mimeType, "UTF-8");
	multipartEntity.addPart(fieldName, contentBody);
	HttpEntity upstreamEntity = multipartEntity;
	HttpResponse response = put(url, upstreamEntity);
	try {
	    EntityUtils.consume(upstreamEntity);
	} catch (IOException e) {
	    throw new RequestException(e);
	}
	expectStatusCode(response, 200, 201, 204);
	return response;
    }

    public HttpResponse put(String url, HttpEntity entity) throws RequestException {
	HttpResponse response = null;
	HttpPut put = null;
	try {
	    // sending a short put to trigger authentication and storing
	    // httpcontext.
	    put = new HttpPut(url);
	    // HttpContext localContext = new BasicHttpContext();
	    // HttpEntity smallEntity = new StringEntity("someBytes",
	    // "application/xml", "UTF-8");
	    // put.setEntity(smallEntity);
	    // response = excute(put, localContext);
	    // EntityUtils.consume(smallEntity);
	    // consume(response);
	    // put = new HttpPut(url);
	    put.setEntity(entity);
	    response = executeWithContext(put);
	    EntityUtils.consume(entity);
	} catch (IOException e) {
	    put.abort();
	    throw new RequestException(e);
	}
	System.out.println("StatusCode received: [" + response.getStatusLine().getStatusCode() + "]");
	expectStatusCode(response, 200);
	return response;
    }

    public HttpResponse post(String url, String string, String mimeType) throws RequestException {
	try {
	    return post(url, new StringEntity(string, mimeType, DEFAULT_CHARSET));
	} catch (UnsupportedEncodingException e) {
	    throw new RequestException(e);
	}
    }

    public HttpResponse post(String url, File file, String mimeType) throws RequestException {
	return post(url, new FileEntity(file, mimeType));
    }

    public HttpResponse post(String url, File file, String mimeType, IProgressMonitor progressMonitor)
	    throws RequestException {
	HttpResponse response = post(url, new FileEntity(file, mimeType));
	return response;
    }

    public HttpResponse post(String url, HttpEntity entity) throws RequestException {
	HttpResponse response = null;
	try {
	    final HttpPost post = new HttpPost(url);
	    post.setEntity(entity);
	    response = executeWithContext(post);
	    EntityUtils.consume(entity);
	} catch (Exception e) {
	    throw new RequestException(e);
	}
	expectStatusCode(response, 200, 201);
	return response;
    }

    public HttpResponse delete(String url) throws RequestException {
	HttpResponse response = null;
	try {
	    final HttpDelete delete = new HttpDelete(url);
	    response = executeWithContext(delete);
	} catch (Exception e) {
	    throw new RequestException(e);
	}
	expectStatusCode(response, 200, 204);
	return response;
    }

    /**
     * Releases the connection and has to be called by clients of this class
     * after calling any of the HTTP-methods.
     * 
     * @param httpResponse
     *            its entity will be consumed using {@link EntityUtils}
     */
    public void consume(HttpResponse httpResponse) {
	try {
	    EntityUtils.consume(httpResponse.getEntity());
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}
    }

    private static void expectStatusCode(HttpResponse response, Integer... expectedStatusCodes) throws RequestException {
	int actualStatusCode = response.getStatusLine().getStatusCode();
	for (Integer expectedStatusCode : expectedStatusCodes) {
	    if (actualStatusCode == expectedStatusCode)
		return;
	}

	// we close the connection, and eat any IO exceptions
	String body = null;
	try {
	    body = IOUtils.toString(response.getEntity().getContent());
	    EntityUtils.consume(response.getEntity());
	} catch (IOException e) {
	    e.printStackTrace();
	}
	throw new RequestException(actualStatusCode, body);
    }

    public void shutdown() {
	idleConnectionMonitor.shutdown();
	client.getConnectionManager().shutdown();
    }

    private HttpResponse executeWithContext(HttpUriRequest request) throws ClientProtocolException, IOException {
	return client.execute(request, context);
    }

    /**
     * Closes expired connections and releases idle connections.
     */
    public static class IdleConnectionMonitor extends Thread {

	private final ClientConnectionManager cm;
	private volatile boolean shutdown;

	public IdleConnectionMonitor(ClientConnectionManager cm) {
	    super();
	    this.cm = cm;
	}

	@Override
	public void run() {
	    try {
		while (!shutdown) {
		    synchronized (this) {
			Thread.sleep(10000);
			// Close expired connections
			cm.closeExpiredConnections();
			// Close connections that have been idle to long
			cm.closeIdleConnections(60, TimeUnit.SECONDS);
		    }
		}
	    } catch (InterruptedException ex) {
		// terminate
	    }
	}

	public void shutdown() {
	    shutdown = true;
	    synchronized (this) {
		notifyAll();
	    }
	}

    }

}