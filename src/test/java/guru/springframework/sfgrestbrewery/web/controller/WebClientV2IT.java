package guru.springframework.sfgrestbrewery.web.controller;

import guru.springframework.sfgrestbrewery.web.functional.BeerRouterConfig;
import guru.springframework.sfgrestbrewery.web.model.BeerDto;
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

import static guru.springframework.sfgrestbrewery.bootstrap.BeerLoader.BEER_2_UPC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class WebClientV2IT {
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
    void testUpdateBeer() throws InterruptedException {
        final String newBeerName = "eum602 - new Beer name";
        final Integer beerId = 1;
        CountDownLatch countDownLatch = new CountDownLatch(2);

        webClient.put().uri(BeerRouterConfig.BEER_V2_URL + "/" + beerId)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(
                        BeerDto
                                .builder()
                                .beerName(newBeerName)
                                .upc("12345987")
                                .beerStyle("PALE_ALE")
                                .price(new BigDecimal("1.39"))
                                .build()))
                .retrieve().toBodilessEntity()
                .subscribe(responseEntity -> {
                    assertThat(responseEntity.getStatusCode().is2xxSuccessful());
                    countDownLatch.countDown();
                })
        ;
        //wait for update THREAD to complete, otherwise the WebClient.get operation THREAD could read the old value faster
        //and that is not a desired behaviour
        countDownLatch.await(500, TimeUnit.MILLISECONDS);//this is one way to handle multiple threads where one
        // has to finish in order for another to start

        webClient.get().uri(BeerRouterConfig.BEER_V2_URL + "/" + beerId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(BeerDto.class)
                .subscribe(beer -> {
                    assertThat(beer).isNotNull();
                    assertThat(beer.getBeerName()).isNotNull();
                    assertThat(beer.getBeerName()).isEqualTo(newBeerName);
                    countDownLatch.countDown();
                });

        countDownLatch.countDown();
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void updateBeerNotFound() throws InterruptedException {
        final String newBeerName = "eum602 new name";
        final Integer beerId = 123;
        CountDownLatch countDownLatch = new CountDownLatch(1);

        webClient
                .put()
                .uri(BeerRouterConfig.BEER_V2_URL + "/" + beerId)
                .accept(MediaType.APPLICATION_JSON)
                .body(
                        BodyInserters
                        .fromValue(
                                BeerDto
                                        .builder()
                                .beerName(newBeerName)
                                .upc("123456")
                                .beerStyle("PALE_ALE")
                                .price(new BigDecimal("8.99"))
                                .build()
                        )
                )
                .retrieve()
                .toBodilessEntity()
                .subscribe(responseEntity -> {
                    assertThat(responseEntity.getStatusCode().is2xxSuccessful());
                },throwable -> {//thrown if the response is not with a 200 status
                    countDownLatch.countDown();
                });
        countDownLatch.await(1000,TimeUnit.MILLISECONDS);
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void getBeerById() throws InterruptedException{
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Mono<BeerDto> beerDtoMono = webClient.get().uri( BeerRouterConfig.BEER_V2_URL + "/" + 1)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(BeerDto.class);

        beerDtoMono.subscribe(beer -> {
            assertThat(beer).isNotNull();
            assertThat(beer.getBeerName()).isNotNull();

            countDownLatch.countDown();
        });

        countDownLatch.await(2000, TimeUnit.MILLISECONDS); //countDown only waits for 1 second
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void getBeerByIdNotFound() throws InterruptedException{
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Mono<BeerDto> beerDtoMono = webClient.get().uri( BeerRouterConfig.BEER_V2_URL + "/" + 1345)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(BeerDto.class);
        beerDtoMono.subscribe(beer -> {
        },throwable -> {
            countDownLatch.countDown();
        });

        countDownLatch.await(1000, TimeUnit.MILLISECONDS); //countDown only waits for 1 second
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void getBeerByUpc() throws InterruptedException{
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Mono<BeerDto> beerDtoMono = webClient.get().uri( BeerRouterConfig.BEER_V2_URL_UPC + "/" + BEER_2_UPC)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(BeerDto.class);

        beerDtoMono.subscribe(beer -> {
            assertThat(beer).isNotNull();
            assertThat(beer.getBeerName()).isNotNull();

            countDownLatch.countDown();
        });

        countDownLatch.await(2000, TimeUnit.MILLISECONDS); //countDown only waits for 1 second
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void getBeerByUpcNotFound() throws InterruptedException{
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Mono<BeerDto> beerDtoMono = webClient.get().uri( BeerRouterConfig.BEER_V2_URL_UPC + "/" + 1234)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(BeerDto.class);
        beerDtoMono.subscribe(beer -> {
        },throwable -> {
            countDownLatch.countDown();
        });

        countDownLatch.await(1000, TimeUnit.MILLISECONDS); //countDown only waits for 1 second
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void testSaveBeer() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        BeerDto beerDto = BeerDto
                .builder()
                .beerName("eum602 new")
                .upc("123456789")
                .beerStyle("PALE_ALE")
                .price(new BigDecimal("6.40"))
                .build();
        Mono<ResponseEntity<Void>> beerResponseMono = webClient.post().uri(BeerRouterConfig.BEER_V2_URL)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(beerDto))
                .retrieve().toBodilessEntity();
        beerResponseMono.publishOn(Schedulers.parallel()).subscribe(responseEntity -> {
            assertThat(responseEntity.getStatusCode().is2xxSuccessful());
            countDownLatch.countDown();
        });
        countDownLatch.await(1000,TimeUnit.MILLISECONDS);
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void testSaveBeerBadRequest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        BeerDto beerDto = BeerDto
                .builder()
                //.beerName("eum602 new") //omitting this field- to cause an error - since it is a mandatory field into the beer object defined into the model package
                .upc("123456789")
                //.beerStyle("PALE_ALE")
                .price(new BigDecimal("6.40"))
                .build();
        Mono<ResponseEntity<Void>> beerResponseMono = webClient.post().uri(BeerRouterConfig.BEER_V2_URL)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(beerDto))
                .retrieve().toBodilessEntity();
        beerResponseMono.subscribe(responseEntity -> {
        },throwable -> {
            if (throwable.getClass().getName().equals("org.springframework.web.reactive.function.client.WebClientResponseException$BadRequest")){
                WebClientResponseException ex = (WebClientResponseException) throwable;
                if (ex.getStatusCode().equals(HttpStatus.BAD_REQUEST)){
                    countDownLatch.countDown();
                }
            }
        });
        countDownLatch.await(1000,TimeUnit.MILLISECONDS);
        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    void testDeleteBeer(){
        Integer beerId = 3;
        CountDownLatch countDownLatch = new CountDownLatch(1);

        webClient.delete().uri(BeerRouterConfig.BEER_V2_URL + "/" + beerId)
                .retrieve().toBodilessEntity()
                .flatMap(responseEntity -> {
                    countDownLatch.countDown();

                    return webClient.get().uri(BeerRouterConfig.BEER_V2_URL + "/" + beerId)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve().bodyToMono(BeerDto.class);
                }).subscribe(deletedBeerDto -> {},throwable -> { //should fall here since the get operation should fail because
                    //the element has just been deleted
                    countDownLatch.countDown();
        });
    }

    @Test
    void testDeleteBeerNotFound() {
        Integer beerId = 4;
        webClient.delete().uri(BeerRouterConfig.BEER_V2_URL + "/" + beerId)
                .retrieve().toBodilessEntity().block();
        assertThrows(WebClientResponseException.NotFound.class, () -> { //repeating the operation again to make sure it will be thrown
            //since the element has just already been deleted
            webClient.delete().uri(BeerRouterConfig.BEER_V2_URL + "/" +beerId)
                    .retrieve().toBodilessEntity().block();
        });
    }
}
