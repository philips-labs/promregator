package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.LinkedList;
import java.util.concurrent.TimeoutException;

import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.util.concurrent.RateLimiter;

import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

class ReactiveCFPaginatedRequestFetcherTest {
	
	private InternalMetrics internalMetricsMocked = Mockito.mock(InternalMetrics.class);
	
	private static PaginatedRequestGeneratorFunction<ListOrganizationsRequest> requestGenerator;
	private static PaginatedResponseGeneratorFunction<OrganizationResource, ListOrganizationsResponse> responseGenerator;
	
	static {
		requestGenerator = (orderDirection, resultsPerPage, pageNumber) ->
				ListOrganizationsRequest.builder()
				.orderDirection(orderDirection)
				.resultsPerPage(resultsPerPage)
				.page(pageNumber)
				.build();

		responseGenerator = (data, numberOfPages) ->
				ListOrganizationsResponse.builder()
				.resources(data)
				.totalPages(numberOfPages)
				.totalResults(data.size())
				.build();

	}
	
	@Test
	void testEmptyResponse() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked, Double.MAX_VALUE, Duration.ofMillis(100));
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval(RequestType.OTHER, "nokey", requestGenerator, request -> {
			ListOrganizationsResponse response = ListOrganizationsResponse.builder()
					.resources(new LinkedList<>())
					.totalPages(1)
					.totalResults(0)
					.build();
			
			return Mono.just(response);
		}, 100, responseGenerator);
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.block();
		Assertions.assertEquals(1, subjectResponse.getTotalPages().intValue());
		Assertions.assertEquals(0, subjectResponse.getTotalResults().intValue());
		Assertions.assertNotNull(subjectResponse.getResources());
		Assertions.assertTrue (subjectResponse.getResources().isEmpty());
	}
	
	@Test
	void testWithContentOnePage() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked, Double.MAX_VALUE, Duration.ofMillis(100));
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval(RequestType.OTHER, "nokey", requestGenerator, request -> {
			LinkedList<OrganizationResource> list = new LinkedList<>();
			list.add(OrganizationResource.builder().build());
			list.add(OrganizationResource.builder().build());
			
			ListOrganizationsResponse response = ListOrganizationsResponse.builder()
					.resources(list)
					.totalPages(1)
					.totalResults(2)
					.build();
			
			return Mono.just(response);
		}, 100, responseGenerator);
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.block();
		Assertions.assertEquals(1, subjectResponse.getTotalPages().intValue());
		Assertions.assertEquals(2, subjectResponse.getTotalResults().intValue());
		Assertions.assertNotNull (subjectResponse.getResources());
		Assertions.assertEquals(2, subjectResponse.getResources().size());
	}
	
	@Test
	void testWithContentTwoPages() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked, 0, Duration.ofMillis(100));
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval(RequestType.OTHER, "nokey", requestGenerator, request -> {
			LinkedList<OrganizationResource> list = new LinkedList<>();
			
			for (int i = 0;i<request.getResultsPerPage(); i++) {
				list.add(OrganizationResource.builder().build());
			}
			
			ListOrganizationsResponse response = ListOrganizationsResponse.builder()
					.resources(list)
					.totalPages(2)
					.totalResults(2 * request.getResultsPerPage())
					.build();
			
			return Mono.just(response);
		}, 100, responseGenerator);
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.block();
		Assertions.assertEquals(2, subjectResponse.getTotalPages().intValue());
		Assertions.assertEquals(2*100, subjectResponse.getTotalResults().intValue());
		Assertions.assertNotNull(subjectResponse.getResources());
		Assertions.assertEquals(2*100, subjectResponse.getResources().size());
	}
	
	@Test
	void testWithContentTwoPagesSecondWithoutItems() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked, Double.MAX_VALUE, Duration.ofMillis(100));
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval(RequestType.OTHER, "nokey", requestGenerator, request -> {
			LinkedList<OrganizationResource> list = new LinkedList<>();
			
			if (request.getPage() == 1) {
				for (int i = 0;i<request.getResultsPerPage(); i++) {
					list.add(OrganizationResource.builder().build());
				}
			}
			
			ListOrganizationsResponse response = ListOrganizationsResponse.builder()
					.resources(list)
					.totalPages(2)
					.totalResults( (2 - request.getPage()) * request.getResultsPerPage()) // that's tricky&ugly, but correct: It would be the answer, if suddenly the items on the 2nd page vanished
					.build();
			
			return Mono.just(response);
		}, 100, responseGenerator);
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.block();
		Assertions.assertEquals(2, subjectResponse.getTotalPages().intValue());
		Assertions.assertEquals(1*100, subjectResponse.getTotalResults().intValue());
		Assertions.assertNotNull (subjectResponse.getResources());
		Assertions.assertEquals(1*100, subjectResponse.getResources().size());
	}
	
	@Test
	void testWithContentTimeoutOnFirstPage() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked, Double.MAX_VALUE, Duration.ofMillis(100));
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval(RequestType.OTHER, "nokey", requestGenerator, request ->
				Mono.error(new TimeoutException()), 100, responseGenerator);
		
		ListOrganizationsResponse fallback = ListOrganizationsResponse.builder().build();
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.doOnError(e ->
			Assertions.assertTrue(Exceptions.unwrap(e) instanceof TimeoutException)
		).onErrorReturn(fallback).block();
		Assertions.assertEquals(fallback, subjectResponse);
	}
	
	@Test
	void testWithContentTimeoutOnSecondPage() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked, Double.MAX_VALUE, Duration.ofMillis(100));
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval(RequestType.OTHER, "nokey", requestGenerator, request -> {
			if (request.getPage() == 2) {
				return Mono.error(new TimeoutException());
			}
			
			LinkedList<OrganizationResource> list = new LinkedList<>();
			
			if (request.getPage() == 1) {
				for (int i = 0;i<request.getResultsPerPage(); i++) {
					list.add(OrganizationResource.builder().build());
				}
			}
			
			ListOrganizationsResponse response = ListOrganizationsResponse.builder()
					.resources(list)
					.totalPages(2)
					.totalResults(2 * request.getResultsPerPage()) 
					.build();
			
			return Mono.just(response);
		}, 100, responseGenerator);
		
		ListOrganizationsResponse fallback = ListOrganizationsResponse.builder().build();
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.doOnError(e ->
			Assertions.assertTrue(Exceptions.unwrap(e) instanceof TimeoutException)
		).onErrorReturn(fallback).block();
		Assertions.assertEquals(fallback, subjectResponse);
	}

	@Test
	void testWithContentGeneralException() {
		ReactiveCFPaginatedRequestFetcher subject = new ReactiveCFPaginatedRequestFetcher(this.internalMetricsMocked, Double.MAX_VALUE, Duration.ofMillis(100));
		
		Mono<ListOrganizationsResponse> subjectResponseMono = subject.performGenericPagedRetrieval(RequestType.OTHER, "nokey", requestGenerator, request ->
			Mono.error(new Exception()), 100, responseGenerator);
		
		ListOrganizationsResponse fallback = ListOrganizationsResponse.builder().build();
		
		ListOrganizationsResponse subjectResponse = subjectResponseMono.doOnError(e ->
			Assertions.assertTrue(Exceptions.unwrap(e) instanceof Exception)
		).onErrorReturn(fallback).block();
		Assertions.assertEquals(fallback, subjectResponse);
	}
	
	@Test
	void testInfiniteRateLimitPossible() {
		RateLimiter rl = RateLimiter.create(Double.POSITIVE_INFINITY);
		
		boolean acquired = rl.tryAcquire(10000, Duration.ofMillis(100));
		Assertions.assertTrue(acquired);
	}
}
