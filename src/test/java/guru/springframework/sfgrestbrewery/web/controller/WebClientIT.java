package guru.springframework.sfgrestbrewery.web.controller;

import guru.springframework.sfgrestbrewery.web.model.BeerDto;
import guru.springframework.sfgrestbrewery.web.model.BeerPagedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static guru.springframework.sfgrestbrewery.bootstrap.BeerLoader.BEER_1_UPC;
import static guru.springframework.sfgrestbrewery.bootstrap.BeerLoader.BEER_2_UPC;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by jt on 3/7/21.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT) //brings full spring context along with the test environment
public class WebClientIT {

    //Integration tests

    public static final String BASE_URL = "http://localhost:8080";

    WebClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().wiretap(true)))
                .build();
    }

    @Test
    void testDeleteBeer() throws InterruptedException{
        CountDownLatch countDownLatch = new CountDownLatch(3);
        //webClient.get().uri("/api/v1/beer") ----> get a list of beers
        webClient.get().uri("/api/v1/beer")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(BeerPagedList.class)
                .publishOn(Schedulers.single())
                .subscribe(pagedList -> {
                    countDownLatch.countDown();
                    BeerDto beerDto = pagedList.getContent().get(0);//get the first beer
                    webClient.delete().uri("/api/v1/beer/" + beerDto.getId())
                            .retrieve().toBodilessEntity()
                            .flatMap(responseEntity -> {
                                countDownLatch.countDown();
                                return webClient.get().uri("/api/v1/beer/" + beerDto.getId()) //get a beer that has been deleted
                                        .accept(MediaType.APPLICATION_JSON)
                                        .retrieve().bodyToMono(BeerDto.class);
                            }).subscribe(savedDto -> {

                    },throwable -> {//because an exception is expected to be thrown since te beer no longer exist in the db
                                countDownLatch.countDown();
                    });
                });
        countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void testSaveBeer() throws InterruptedException{
        CountDownLatch countDownLatch = new CountDownLatch(1);

        BeerDto beerDto = BeerDto.builder()
                .beerName("eum602 Beer")
                .upc("12345678")
                .beerStyle("PALE_ALE")
                .price(new BigDecimal("9.89"))
                .build();
        Mono<ResponseEntity<Void>> beerResponseMono =  webClient.post().uri("/api/v1/beer")
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(beerDto))
                .retrieve().toBodilessEntity();
        beerResponseMono.publishOn(Schedulers.parallel()).subscribe(responseEntity -> {
           assertThat(responseEntity.getStatusCode().is2xxSuccessful());
           countDownLatch.countDown();
        });

        countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void testSaveBeerBadRequest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        BeerDto beerDto = BeerDto.builder()
                .price(new BigDecimal("9.08"))
                .build();
        Mono<ResponseEntity<Void>> beerResponseMono = webClient.post().uri("/api/v1/beer")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();

        beerResponseMono.publishOn(Schedulers.parallel()).doOnError(throwable -> {
            countDownLatch.countDown();
        }).subscribe(responseEntity -> {

        });
        countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void testUpdateBeer() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        webClient.get().uri("/api/v1/beer")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(BeerPagedList.class)
                .publishOn(Schedulers.single())
                .subscribe(pagedList -> {//get the list from the db
                    countDownLatch.countDown();
                    //get an existing beer
                    BeerDto beerDto =  pagedList.getContent().get(0); //assumes there exist something at index zero in the db
                    BeerDto updatedPayload = BeerDto.builder().beerName("eum602Update") //overriding the existing beer name
                            .beerStyle(beerDto.getBeerStyle())
                            .upc(beerDto.getUpc())
                            .price(beerDto.getPrice())
                            .build();
                    //Update existing beer
                    webClient.put().uri("/api/v1/beer/" + beerDto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(BodyInserters.fromValue(updatedPayload))
                            .retrieve().toBodilessEntity()
                            .flatMap(responseEntity -> {
                                //get and verify update
                                countDownLatch.countDown();
                                return webClient.get().uri("/api/v1/beer/" + beerDto.getId())
                                        .accept(MediaType.APPLICATION_JSON)
                                        .retrieve().bodyToMono(BeerDto.class);
                            }).subscribe(savedDto -> {
                                assertThat(savedDto.getBeerName()).isEqualTo("eum602Update");
                                countDownLatch.countDown();
                    });
                });
        countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void testUpdateBeerNotFound() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        BeerDto updatePayload = BeerDto
                .builder()
                .beerName("eum602Update")
                .beerStyle("PALE_ALE")
                .upc("12345678")
                .price(new BigDecimal("9.99"))
                .build();
        webClient.put().uri("/api/v1/beer" + 200)//passing an index "200" which does not exist in the db
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(updatePayload))
                .retrieve().toBodilessEntity()
                .subscribe(responseEntity -> {//doing nothing here since this test wants to test the exception condition
                },throwable -> {//setting an exception condition
                    if (throwable.getClass().getName().equals("org.springframework.web.reactive.function.client.WebClientResponseException$NotFound")){
                        WebClientResponseException ex = (WebClientResponseException) throwable;
                         if (ex.getStatusCode().equals(HttpStatus.NOT_FOUND)){
                             countDownLatch.countDown();
                         }
                    }
                });
        countDownLatch.countDown();

        countDownLatch.await(1000,TimeUnit.MILLISECONDS);
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void getBeerById() throws InterruptedException{
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Mono<BeerDto> beerDtoMono = webClient.get().uri("/api/v1/beer/1")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(BeerDto.class);
        beerDtoMono.subscribe(beer -> {
            assertThat(beer).isNotNull();
            assertThat(beer.getBeerName()).isNotNull();

            countDownLatch.countDown();
        });

        countDownLatch.await(1000, TimeUnit.MILLISECONDS); //countDown only waits for 1 second
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void getBeerByUpc() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Mono<BeerDto> beerDtoMono = webClient.get().uri("/api/v1/beerUpc/" +  BEER_2_UPC)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(BeerDto.class);
        beerDtoMono.subscribe(beer -> {
            assertThat(beer).isNotNull();
            assertThat(beer.getBeerName()).isNotNull();

            countDownLatch.countDown();
        });

        countDownLatch.await(1000,TimeUnit.MILLISECONDS);
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void testListBeers() throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(1);

        Mono<BeerPagedList> beerPagedListMono = webClient.get().uri("/api/v1/beer")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(BeerPagedList.class);


//        BeerPagedList pagedList = beerPagedListMono.block();
//        pagedList.getContent().forEach(beerDto -> System.out.println(beerDto.toString()));
        beerPagedListMono.publishOn(Schedulers.parallel()).subscribe(beerPagedList -> {

            beerPagedList.getContent().forEach(beerDto -> System.out.println(beerDto.toString()));

            countDownLatch.countDown();
        });

        countDownLatch.await();
    }
}
