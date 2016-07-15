/*
 * Copyright 2012-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package be.ordina.msdashboard.aggregators.health;

import be.ordina.msdashboard.aggregators.ErrorHandler;
import be.ordina.msdashboard.aggregators.NettyServiceCaller;
import be.ordina.msdashboard.model.Node;
import be.ordina.msdashboard.uriresolvers.UriResolver;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import rx.Observable;
import rx.observers.TestSubscriber;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static be.ordina.msdashboard.constants.Constants.CONFIGSERVER;
import static be.ordina.msdashboard.constants.Constants.DISCOVERY;
import static be.ordina.msdashboard.constants.Constants.DISK_SPACE;
import static be.ordina.msdashboard.constants.Constants.HYSTRIX;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

/**
 * @author Andreas Evers
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(HealthToNodeConverter.class)
public class HealthIndicatorsAggregatorTest {

    @InjectMocks
    private HealthIndicatorsAggregator aggregator;

    @Mock
    private DiscoveryClient discoveryClient;
    @Mock
    private UriResolver uriResolver;
    @Mock
    private HealthProperties properties;
    @Mock
    private NettyServiceCaller caller;
    @Mock
    private ErrorHandler errorHandler;
    @Captor
    private ArgumentCaptor<HttpClientRequest> requestCaptor;

    @Test
    public void aggregatesEverything() {

    }

    @Test
    public void shouldGetHealthNodesFromService() {
        when(properties.getRequestHeaders()).thenReturn(requestHeaders());
        Map retrievedMap = new HashMap();
        Observable retrievedMapObservable = Observable.just(retrievedMap);
        when(caller.retrieveJsonFromRequest(anyString(), any(HttpClientRequest.class)))
                .thenReturn(retrievedMapObservable);
        mockStatic(HealthToNodeConverter.class);
        PowerMockito.when(HealthToNodeConverter.convertToNodes(anyString(), anyMap()))
                .thenReturn(Observable.from(correctNodes()));

        TestSubscriber<Node> testSubscriber = new TestSubscriber<>();
        aggregator.getHealthNodesFromService("testService", "testUrl").toBlocking().subscribe(testSubscriber);
        List<Node> nodes = testSubscriber.getOnNextEvents();

        verify(caller, times(1)).retrieveJsonFromRequest(eq("testService"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getHeaders().entries()).usingElementComparator(stringEntryComparator())
                .containsExactlyElementsOf(requestHeaders().entrySet());
        assertThat(nodes).containsOnly(new Node("Node1"), new Node("Node2"));
    }

    // shouldFailCompletelyOnBadHeaders

    @Test(expected = RuntimeException.class)
    public void shouldFailEntireHealthNodeRetrievalChainOnGlobalRuntimeExceptions() {
        when(properties.getRequestHeaders()).thenReturn(requestHeaders());
        when(caller.retrieveJsonFromRequest(anyString(), any(HttpClientRequest.class)))
                .thenThrow(new RuntimeException());

        TestSubscriber<Node> testSubscriber = new TestSubscriber<>();
        aggregator.getHealthNodesFromService("testService", "testUrl").toBlocking().subscribe(testSubscriber);
        testSubscriber.getOnNextEvents();
        testSubscriber.assertCompleted();
    }

    @Test
    public void shouldReturnEmptyObservableOnEmptySourceObservable() {
        when(properties.getRequestHeaders()).thenReturn(requestHeaders());
        Observable retrievedMapObservable = Observable.empty();
        when(caller.retrieveJsonFromRequest(anyString(), any(HttpClientRequest.class)))
                .thenReturn(retrievedMapObservable);

        TestSubscriber<Node> testSubscriber = new TestSubscriber<>();
        aggregator.getHealthNodesFromService("testService", "testUrl").toBlocking().subscribe(testSubscriber);
        testSubscriber.assertNoValues();
        testSubscriber.assertCompleted();

        verify(caller, times(1)).retrieveJsonFromRequest(eq("testService"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getHeaders().entries()).usingElementComparator(stringEntryComparator())
                .containsExactlyElementsOf(requestHeaders().entrySet());
    }

    @Test
    public void shouldReturnEmptyObservableOnErroneousSourceObservable() {
        when(properties.getRequestHeaders()).thenReturn(requestHeaders());
        Map retrievedMap = new HashMap();
        Observable retrievedMapObservable = Observable.just(retrievedMap).publish().autoConnect();
        retrievedMapObservable.map(o -> o.toString());
        when(caller.retrieveJsonFromRequest(anyString(), any(HttpClientRequest.class)))
                .thenReturn(retrievedMapObservable);

        TestSubscriber<Node> testSubscriber = new TestSubscriber<>();
        aggregator.getHealthNodesFromService("testService", "testUrl").toBlocking().subscribe(testSubscriber);
        testSubscriber.assertNoValues();
        testSubscriber.assertCompleted();

        verify(caller, times(1)).retrieveJsonFromRequest(eq("testService"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getHeaders().entries()).usingElementComparator(stringEntryComparator())
                .containsExactlyElementsOf(requestHeaders().entrySet());
    }

    @Test
    public void shouldReturnEmptyObservableOnExceptionsInSourceObservable() {
        when(properties.getRequestHeaders()).thenReturn(requestHeaders());
        Map retrievedMap = new HashMap();
        Observable retrievedMapObservable = Observable.just(retrievedMap);
        retrievedMapObservable.doOnNext(o -> {throw new RuntimeException();});
        when(caller.retrieveJsonFromRequest(anyString(), any(HttpClientRequest.class)))
                .thenReturn(retrievedMapObservable);

        TestSubscriber<Node> testSubscriber = new TestSubscriber<>();
        aggregator.getHealthNodesFromService("testService", "testUrl").toBlocking().subscribe(testSubscriber);
        testSubscriber.getOnNextEvents();
        testSubscriber.assertNoValues();
        testSubscriber.assertCompleted();

        verify(caller, times(1)).retrieveJsonFromRequest(eq("testService"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getHeaders().entries()).usingElementComparator(stringEntryComparator())
                .containsExactlyElementsOf(requestHeaders().entrySet());
    }

    @Test
    public void shouldReturnEmptyObservableOnErroneousConversion() {
        when(properties.getRequestHeaders()).thenReturn(requestHeaders());
        Map retrievedMap = new HashMap();
        Observable retrievedMapObservable = Observable.just(retrievedMap).publish().autoConnect();
        when(caller.retrieveJsonFromRequest(anyString(), any(HttpClientRequest.class)))
                .thenReturn(retrievedMapObservable);
        mockStatic(HealthToNodeConverter.class);
        PowerMockito.when(HealthToNodeConverter.convertToNodes(anyString(), anyMap()))
                .thenThrow(new RuntimeException("Error1"));

        TestSubscriber<Node> testSubscriber = new TestSubscriber<>();
        aggregator.getHealthNodesFromService("testService", "testUrl").toBlocking().subscribe(testSubscriber);
        testSubscriber.getOnNextEvents();
        testSubscriber.assertNoValues();
        testSubscriber.assertCompleted();

        verify(caller, times(1)).retrieveJsonFromRequest(eq("testService"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getHeaders().entries()).usingElementComparator(stringEntryComparator())
                .containsExactlyElementsOf(requestHeaders().entrySet());
    }

    private Map<String,String> requestHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/hal+json");
        headers.put("Accept-Language", "en-us,en;q=0.5");
        return headers;
    }

    private Node[] correctNodes() {
        return new Node[] { new Node("Node1"), new Node(HYSTRIX), new Node(DISK_SPACE),
                new Node(DISCOVERY), new Node(CONFIGSERVER), new Node("Node2") };
    }

    private Comparator stringEntryComparator() {
        return (Comparator<Map.Entry<String, String>>) (o1, o2) ->
                (o1.getKey() + "|" + o1.getValue()).compareTo(o2.getKey() + "|" + o2.getValue());
    }

    @Test
    public void shouldGetServiceIdsFromDiscoveryClient() {
        when(discoveryClient.getServices()).thenReturn(asList("svc1","SVC2","zuul","svc3"));

        TestSubscriber<String> testSubscriber = new TestSubscriber<>();
        aggregator.getServiceIdsFromDiscoveryClient().toBlocking().subscribe(testSubscriber);
        testSubscriber.assertValues("svc1", "svc2", "svc3");
        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();
    }

    @Test(expected = RuntimeException.class)
    public void shouldFailEntireServiceDiscoveryChainOnGlobalRuntimeExceptions() {
        when(discoveryClient.getServices()).thenThrow(new RuntimeException());

        TestSubscriber<String> testSubscriber = new TestSubscriber<>();
        aggregator.getServiceIdsFromDiscoveryClient().toBlocking().subscribe(testSubscriber);
    }

    @Test
    public void shouldReturnValidServicesOnErroneousDiscovery() {
        when(discoveryClient.getServices()).thenReturn(asList("svc1",null,"zuul","svc3"));

        TestSubscriber<String> testSubscriber = new TestSubscriber<>();
        aggregator.getServiceIdsFromDiscoveryClient().toBlocking().subscribe(testSubscriber);
        testSubscriber.assertValues("svc1", "svc3");
        testSubscriber.assertCompleted();

        verify(errorHandler, times(1)).handleSystemError(anyString(), any(Throwable.class));
    }

    @Test
    public void shouldAggregateNodes() {
        aggregator = spy(new HealthIndicatorsAggregator(discoveryClient, uriResolver, properties, caller, errorHandler));

        Observable observable = Observable.from(asList("svc1",null,"zuul","svc3"));
        doReturn(observable).when(aggregator).getServiceIdsFromDiscoveryClient();
        when(discoveryClient.getInstances(anyString())).then(i -> {
            ServiceInstance serviceInstance = mock(ServiceInstance.class);
            when(serviceInstance.getServiceId()).thenReturn(i.getArgumentAt(0, String.class));
            return asList(serviceInstance);
        });
        when(uriResolver.resolveHealthCheckUrl(any(ServiceInstance.class))).then(i -> i.getArgumentAt(0, ServiceInstance.class).getServiceId());
        doAnswer(i -> Observable.from(asList(i.getArgumentAt(0, String.class)))).when(aggregator).getHealthNodesFromService(anyString(), anyString());

        TestSubscriber<Node> testSubscriber = new TestSubscriber<>();
        aggregator.aggregateNodes().toBlocking().subscribe(testSubscriber);
        testSubscriber.getOnNextEvents();
        testSubscriber.assertNoValues();
//        testSubscriber.assertCompleted();

    }
}