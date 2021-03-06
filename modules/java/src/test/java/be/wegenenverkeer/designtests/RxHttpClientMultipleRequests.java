package be.wegenenverkeer.designtests;

import be.wegenenverkeer.rxhttp.ClientRequest;
import be.wegenenverkeer.rxhttp.ServerResponse;
import com.jayway.jsonpath.JsonPath;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.observables.BlockingObservable;
import rx.observers.TestSubscriber;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;

/**
 * Created by Karel Maesen, Geovise BVBA on 27/11/15.
 */
public class RxHttpClientMultipleRequests extends UsingWireMock{

    private static Logger LOGGER = LoggerFactory.getLogger(RxHttpClientMultipleRequests.class);

    @Test
    public void demonstrateComposableObservable() throws InterruptedException {
        //set up stubs
        String expectBody = "{ 'contacts': ['contacts/1','contacts/2','contacts/3', 'contacts/4', 'contacts/5', 'contacts/6', 'contacts/7'] }";
        stubFor(get(urlPathEqualTo("/contacts?q=test"))
                .withQueryParam("q", equalTo("test"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withFixedDelay(10)
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(expectBody)));
        stubFor(get(urlPathEqualTo("/contacts/1")).withHeader("Accept", equalTo("application/json")).willReturn(aResponse().withStatus(200).withBody("ONE")));
        stubFor(get(urlPathEqualTo("/contacts/2")).withHeader("Accept", equalTo("application/json")).willReturn(aResponse().withStatus(200).withBody("TWO")));
        stubFor(get(urlPathEqualTo("/contacts/3")).withHeader("Accept", equalTo("application/json")).willReturn(aResponse().withStatus(200).withBody("THREE")));
        stubFor(get(urlPathEqualTo("/contacts/4")).withHeader("Accept", equalTo("application/json")).willReturn(aResponse().withStatus(200).withBody("FOUR")));
        stubFor(get(urlPathEqualTo("/contacts/5")).withHeader("Accept", equalTo("application/json")).willReturn(aResponse().withStatus(200).withBody("FIVE")));
        stubFor(get(urlPathEqualTo("/contacts/6")).withHeader("Accept", equalTo("application/json")).willReturn(aResponse().withStatus(200).withBody("SIX")));
        stubFor(get(urlPathEqualTo("/contacts/7")).withHeader("Accept", equalTo("application/json")).willReturn(aResponse().withStatus(200).withBody("SEVEN")));



        //use case



        String path = "/contacts";
        ClientRequest request = client.requestBuilder()
                .setMethod("GET")
                .setUrlRelativetoBase(path)
                .addQueryParam("q", "test")
                .build();

        Function<String, Observable<String>> followLink  = (String contactUrl) -> {
            LOGGER.info("Following contactURL:" + contactUrl);
            ClientRequest followUp = client.requestBuilder()
                    .setMethod("GET")
                    .setUrlRelativetoBase(contactUrl).build();
            return client
                    .executeToCompletion(followUp, ServerResponse::getResponseBody)
                    .finallyDo(() -> LOGGER.info("ContactUrl " + contactUrl + " retrieved"));
        };

        LOGGER.info("Creating Observable...");

        //Here we use concatMap rather than flatMap because this serializes the requests such that requests are
        //made one after the other, and not interleaved. See: http://reactivex.io/documentation/operators/flatmap.html
        Observable<String> observable = client.executeToCompletion(request, ServerResponse::getResponseBody)
                .flatMap(body -> {
                    List<String> l = JsonPath.read(body, "$.contacts");
                    LOGGER.info("Retrieved contact list");
                    return Observable.from(l);
                }).concatMap( contactUrl -> followLink.apply(contactUrl) );

        LOGGER.info("Observable created");

        //verify behaviour
        TestSubscriber<String> sub = new TestSubscriber<>();

        LOGGER.info("Subscribing to Observer");
        observable.subscribe(sub);

        sub.awaitTerminalEvent(DEFAULT_TIME_OUT, TimeUnit.MILLISECONDS);

        sub.assertNoErrors();
        assertEquals(items("ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN"), sub.getOnNextEvents());

    }



}
