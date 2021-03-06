package net.intelie.lognit.cli.http;

import net.intelie.lognit.cli.json.Jsonizer;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import static junit.framework.Assert.fail;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class RestClientImplTest {
    private HttpClient client;
    private MethodFactory methodFactory;
    private Jsonizer jsonizer;
    private RestClient rest;
    private BayeuxFactory bayeuxFactory;

    @Before
    public void setUp() throws Exception {
        client = mock(HttpClient.class, RETURNS_DEEP_STUBS);
        methodFactory = mock(MethodFactory.class, RETURNS_DEEP_STUBS);
        jsonizer = mock(Jsonizer.class);
        bayeuxFactory = mock(BayeuxFactory.class);
        rest = new RestClientImpl(client, methodFactory, bayeuxFactory, jsonizer);
    }

    @Test
    public void whenSettingServer() throws Exception {
        rest.setServer("localhost:9000");

        verify(client.getState(), times(0)).clearCookies();
        assertThat(rest.getServer()).isEqualTo("localhost:9000");
        verifyNoMoreInteractions(client.getParams(), client.getState());
    }

    @Test
    public void whenSettingSameServerWontClearCookies() throws Exception {
        rest.setServer("localhost:9000");
        reset(client.getState());
        rest.setServer("localhost:9000");
        assertThat(rest.getServer()).isEqualTo("localhost:9000");
        verifyNoMoreInteractions(client.getParams(), client.getState());
    }


    @Test
    public void whenAuthenticating() throws Exception {
        rest.authenticate("abc", "123");

        verify(client.getParams()).setAuthenticationPreemptive(true);
        verify(client.getState()).setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("abc", "123"));
        verifyNoMoreInteractions(client.getParams(), client.getState());
    }

    @Test
    public void willExecuteSuccessfulRequest() throws Exception {
        HttpMethod method = mockGet("http://localhost/abc", "HTTP/1.0 200 OK", String.class, "QWEQWE");

        assertThat(rest.get("abc", String.class)).isEqualTo("QWEQWE");

        verify(method, times(0)).getStatusLine();
        verify(method).setDoAuthentication(false);
        verify(client).executeMethod(methodFactory.get("http://localhost/abc"));
    }

    @Test
    public void willExecuteSuccessfulGetStream() throws Exception {
        HttpMethod method = mockGet("http://localhost/abc", "HTTP/1.0 200 OK", String.class, "ABC", "QWE");

        RestStream<String> stream = rest.getStream("abc", String.class);
        assertThat(stream.next()).isEqualTo("ABC");
        assertThat(stream.next()).isEqualTo("QWE");
        assertThat(stream.hasNext()).isFalse();
    }

    @Test
    public void willExecuteSuccessfulGetStreamWithNoBody() throws Exception {
        HttpMethod method = mockGet("http://localhost/abc", "HTTP/1.0 200 OK", String.class);

        RestStream<String> stream = rest.getStream("abc", String.class);
        assertThat(stream.hasNext()).isFalse();
    }

    @Test
    public void willExecuteSuccessfulRequestWithNoBody() throws Exception {
        mockGet("http://localhost/abc", "HTTP/1.0 200 OK", String.class);

        assertThat(rest.get("abc", String.class)).isNull();
    }

    @Test
    public void willExecuteSuccessfulRequestPostWithNoBody() throws Exception {
        mockPost("http://localhost/abc", "HTTP/1.0 200 OK", String.class);

        assertThat(rest.post("abc", new Entity(), String.class)).isNull();
    }

    @Test
    public void willExecuteSuccessfulPost() throws Exception {
        PostMethod method = mockPost("http://localhost/abc", "HTTP/1.0 200 OK", String.class, "QWEQWE");
        Entity entity = mock(Entity.class);
        
        assertThat(rest.post("abc", entity, String.class)).isEqualTo("QWEQWE");
        verify(entity).executeOn(method);

        verify(method, times(0)).getStatusLine();
        verify(method).setDoAuthentication(false);
        verify(client).executeMethod(methodFactory.post("http://localhost/abc"));
    }

    @Test
    public void willExecuteSuccessfulRequestEvenIfIts201() throws Exception {
        HttpMethod method = mockGet("http://localhost/abc", "HTTP/1.0 201 OK", String.class, "QWEQWE");

        assertThat(rest.get("abc", String.class)).isEqualTo("QWEQWE");

        verify(method, times(0)).getStatusLine();
        verify(method).setDoAuthentication(false);
        verify(client).executeMethod(methodFactory.get("http://localhost/abc"));
    }


    @Test
    public void willExecuteSuccessfulRequestAuthenticating() throws Exception {
        HttpMethod method = mockGet("http://someserver:9000/abc", "HTTP/1.0 200 OK", String.class, "QWEQWE");

        rest.setServer("someserver:9000");
        rest.authenticate("abc", "qwe");
        assertThat(rest.get("abc", String.class)).isEqualTo("QWEQWE");

        verify(method, times(0)).getStatusLine();
        verify(method, times(1)).setDoAuthentication(true);
        verify(client).executeMethod(methodFactory.get("http://someserver:9000/abc"));
    }

    @Test
    public void willIgnoreExtraSlash() throws Exception {
        HttpMethod method = mockGet("http://someserver:9000/abc", "HTTP/1.0 200 OK", String.class, "QWEQWE");

        rest.setServer("someserver:9000");
        rest.authenticate("abc", "qwe");
        assertThat(rest.get("/abc", String.class)).isEqualTo("QWEQWE");

        verify(method, times(0)).getStatusLine();
        verify(method, times(1)).setDoAuthentication(true);
        verify(client).executeMethod(methodFactory.get("http://someserver:9000/abc"));
    }

    @Test
    public void canSaveAuthenticationStatus() throws Exception {
        Cookie[] cookies = new Cookie[0];
        when(client.getState().getCookies()).thenReturn(cookies);
        rest.setServer("someserver:9000");

        RestState state = rest.getState();

        assertThat(state.getCookies()).isSameAs(cookies);
        assertThat(state.getServer()).isSameAs("someserver:9000");
    }

    @Test
    public void canRestoreAuthenticationStatus() throws Exception {
        Cookie[] cookies = new Cookie[0];
        RestState state = new RestState(cookies, "abcabc:1211");

        rest.setState(state);
        verify(client.getState()).clearCookies();
        verify(client.getState()).addCookies(cookies);

        HttpMethod method = mockGet("http://abcabc:1211/abc", "HTTP/1.0 200 OK", String.class, "QWEQWE");
        assertThat(rest.get("abc", String.class)).isEqualTo("QWEQWE");
    }

    @Test
    public void willUseCompatibilityToHandleCookies() throws Exception {
        HttpMethod method = mockGet("http://localhost/abc", "HTTP/1.0 200 OK", String.class, "BLABLA");

        rest.get("abc", String.class);

        verify(method.getParams()).setCookiePolicy(CookiePolicy.DEFAULT);
    }

    @Test
    public void willThrowOnUnsuccessfulRequest() throws Exception {
        mockGet("http://localhost/abc", "HTTP/1.0 401 OK", String.class, "BLABLA");

        try {
            rest.get("abc", String.class);
            fail("should throw");
        } catch (UnauthorizedException ex) {
            assertThat(ex).isExactlyInstanceOf(UnauthorizedException.class);
            assertThat(ex.getMessage()).isEqualTo("HTTP/1.0 401 OK");
        }
    }

    @Test
    public void willThrowOnUnsuccessfulRequest301() throws Exception {
        mockGet("http://localhost/abc", "HTTP/1.0 301 OK", String.class, "BLABLA");

        try {
            rest.get("abc", String.class);
            fail("should throw");
        } catch (UnauthorizedException ex) {
            assertThat(ex).isExactlyInstanceOf(UnauthorizedException.class);
            assertThat(ex.getMessage()).isEqualTo("HTTP/1.0 301 OK");
        }
    }

    @Test
    public void willThrowOnUnsuccessfulRequestLessThan100() throws Exception {
        mockGet("http://localhost/abc", "HTTP/1.0 101 OK", String.class, "BLABLA");

        try {
            rest.get("abc", String.class);
            fail("should throw");
        } catch (RequestFailedException ex) {
            assertThat(ex).isExactlyInstanceOf(RequestFailedException.class);
            assertThat(ex.getMessage()).isEqualTo("HTTP/1.0 101 OK");
        }
    }

    @Test
    public void willThrowOnUnsuccessfulRequest501() throws Exception {
        mockGet("http://localhost/abc", "HTTP/1.0 501 OK", String.class, "BLABLA");

        try {
            rest.get("abc", String.class);
            fail("should throw");
        } catch (RequestFailedException ex) {
            assertThat(ex).isExactlyInstanceOf(RequestFailedException.class);
            assertThat(ex.getMessage()).isEqualTo("HTTP/1.0 501 OK");
        }
    }

    private <T> GetMethod mockGet(String url, String line, Class<T> type, T... objects) throws IOException {
        GetMethod method = methodFactory.get(url);
        mockMethod(line, type, method, objects);
        return method;
    }

    private <T> PostMethod mockPost(String url, String line, Class<T> type, T... objects) throws IOException {
        PostMethod method = methodFactory.post(url);
        mockMethod(line, type, method, objects);
        return method;
    }


    private <T> void mockMethod(String line, Class<T> type, HttpMethod method, T... objects) throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream("BLABLA".getBytes());
        if (objects.length > 0)
            when(method.getResponseBodyAsStream()).thenReturn(stream);
        else
            when(method.getResponseBodyAsStream()).thenReturn(null);

        StatusLine statusLine = new StatusLine(line);
        when(method.getStatusLine()).thenReturn(statusLine);
        when(client.executeMethod(method)).thenReturn(statusLine.getStatusCode());

        if (objects.length == 1)
            when(jsonizer.from("BLABLA", type)).thenReturn(objects[0]);
        else if(objects.length > 1)
            when(jsonizer.from(stream, type)).thenReturn(Arrays.asList(objects).iterator());
    }


    @Test
    public void willSetCookiesToCometdServer() throws Exception {
        BayeuxClient bayeux = mock(BayeuxClient.class, RETURNS_DEEP_STUBS);
        when(bayeuxFactory.create("http://server/cometd")).thenReturn(bayeux);

        when(client.getState().getCookies()).thenReturn(new Cookie[]{
                new Cookie("server", "B", "C", "/", 1000, false), new Cookie("server", "E", "F", "/", 1000, false)});

        rest.setServer("server");
        rest.listen("", Object.class, null);

        verify(bayeux).setCookie("B", "C");
        verify(bayeux).setCookie("E", "F");
    }

    @Test
    public void willSelectCorrectCookiesByHostPathAndHttpsWithoutHttps() throws Exception {
        BayeuxClient bayeux = mock(BayeuxClient.class, RETURNS_DEEP_STUBS);
        when(bayeuxFactory.create("http://server/cometd")).thenReturn(bayeux);

        when(client.getState().getCookies()).thenReturn(new Cookie[]{
                new Cookie("server", "A", "C", "/", 1000, false),
                new Cookie("server", "B", "C", "/", 1000, true),
                new Cookie("server", "C", "C", "/test", 1000, false),
                new Cookie("abc", "D", "C", "/", 1000, false)});

        rest.setServer("server");
        rest.listen("", Object.class, null);

        verify(bayeux).setCookie("A", "C");
        verify(bayeux, times(1)).setCookie(anyString(), anyString());
    }

    @Test
    public void willSelectCorrectCookiesByHostPathAndHttpsWithHttps() throws Exception {
        BayeuxClient bayeux = mock(BayeuxClient.class, RETURNS_DEEP_STUBS);
        when(bayeuxFactory.create("https://server:8080/cometd")).thenReturn(bayeux);

        when(client.getState().getCookies()).thenReturn(new Cookie[]{
                new Cookie("server", "A", "C", "/", 1000, false),
                new Cookie("server", "B", "C", "/", 1000, true),
                new Cookie("server", "C", "C", "/test", 1000, false),
                new Cookie("abc", "D", "C", "/", 1000, false)});

        rest.setServer("https://server:8080");
        rest.listen("", Object.class, null);

        verify(bayeux).setCookie("A", "C");
        verify(bayeux).setCookie("B", "C");
        verify(bayeux, times(2)).setCookie(anyString(), anyString());
    }

    @Test
    public void willHandshakeAndRegisterToChannel() throws Exception {
        BayeuxClient bayeux = mock(BayeuxClient.class, RETURNS_DEEP_STUBS);
        when(bayeuxFactory.create("http://server/cometd")).thenReturn(bayeux);

        rest.setServer("server");
        rest.listen("testChannel", Object.class, null);

        verify(bayeux).handshake(120000);
        verify(bayeux.getChannel("testChannel")).subscribe(any(ClientSessionChannel.MessageListener.class));
    }

    @Test
    public void willRegisterAListenerThatDeserializesJSON() throws Exception {
        BayeuxClient bayeux = mock(BayeuxClient.class, RETURNS_DEEP_STUBS);
        when(bayeuxFactory.create("http://server/cometd")).thenReturn(bayeux);

        rest.setServer("server");
        rest.listen("testChannel", Object.class, null);

        verify(bayeux).handshake(120000);
        verify(bayeux.getChannel("testChannel")).subscribe(any(JsonMessageListener.class));
    }

    @Test
    public void willReturnBayeuxListenerHandleWhenListening() throws Exception {
        BayeuxClient bayeux = mock(BayeuxClient.class, RETURNS_DEEP_STUBS);
        when(bayeuxFactory.create("http://server/cometd")).thenReturn(bayeux);

        rest.setServer("server");
        RestListenerHandle handle = rest.listen("testChannel", Object.class, null);

        assertThat(handle).isInstanceOf(BayeuxHandle.class);
    }
}
