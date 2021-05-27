package guru.springframework.sfgrestbrewery.services;

import guru.springframework.sfgrestbrewery.domain.Beer;
import guru.springframework.sfgrestbrewery.repositories.BeerRepository;
import guru.springframework.sfgrestbrewery.web.controller.NotFoundException;
import guru.springframework.sfgrestbrewery.web.mappers.BeerMapper;
import guru.springframework.sfgrestbrewery.web.model.BeerDto;
import guru.springframework.sfgrestbrewery.web.model.BeerPagedList;
import guru.springframework.sfgrestbrewery.web.model.BeerStyleEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.data.r2dbc.query.Criteria.where;

import static org.springframework.data.relational.core.query.Query.empty;
import static org.springframework.data.relational.core.query.Query.query;

/**
 * Created by jt on 2019-04-20.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BeerServiceImpl implements BeerService {
    private final BeerRepository beerRepository;
    private final BeerMapper beerMapper;

    private final R2dbcEntityTemplate template;

    @Cacheable(cacheNames = "beerListCache", condition = "#showInventoryOnHand == false ")
    @Override
    public Mono<BeerPagedList> listBeers(String beerName, BeerStyleEnum beerStyle, PageRequest pageRequest, Boolean showInventoryOnHand) {
        //Mono is returned because this method is simply returning one object
        Query query = null; //a query that will be used to perform the search

        if (!StringUtils.isEmpty(beerName) && !StringUtils.isEmpty(beerStyle)){
            //search both
            query = query(where("beerName").is(beerName).and("beerStyle").is(beerStyle));
        }else if (!StringUtils.isEmpty(beerName) && StringUtils.isEmpty(beerStyle)){
            //search beerServiceName
            query = query(where("beerName").is(beerName));
        }else if (StringUtils.isEmpty(beerName) && !StringUtils.isEmpty(beerStyle)){
            //search beerService Style
            query = query(where("beerStyle").is(beerStyle));
        }else {
            query = empty();
        }

        //template is going to do a reactive query against the database
        //query.with(pageRequest) -> is going to get the limit and offset for paging functionality
        //.map(beerMapper::beerToBeerDto) --> converts each beer into a beerDto
        //.all -> return a flux of beers coming out of the database
        //collect(Collectors.toList()) -> collects into a List
        //finally the 'map'  is used to convert the list of beers into a beer page list.
        return template.select(Beer.class)
                .matching(query.with(pageRequest))
                .all()
                .map(beerMapper::beerToBeerDto)
                .collect(Collectors.toList())
                .map(beers -> new BeerPagedList(beers, PageRequest.of(
                        pageRequest.getPageNumber(),
                        pageRequest.getPageSize()),
                        beers.size()));
    }

    @Cacheable(cacheNames = "beerCache", key = "#beerId", condition = "#showInventoryOnHand == false ")
    @Override
    public Mono<BeerDto> getById(Integer beerId, Boolean showInventoryOnHand) {
        if (showInventoryOnHand) {
            return beerRepository.findById(beerId).map(beerMapper::beerToBeerDtoWithInventory);
        } else {
            return beerRepository.findById(beerId).map(beerMapper::beerToBeerDto);
        }
    }

    @Override
    public Mono<BeerDto> saveNewBeer(BeerDto beerDto) {
        return beerRepository
                .save(beerMapper.beerDtoToBeer(beerDto))
                .map(beerMapper::beerToBeerDto);
    }

    @Override
    public Mono<BeerDto> saveNewBeerMono(Mono<BeerDto> beerDto) {
        return beerDto.map(beerMapper::beerDtoToBeer)
                .flatMap(beerRepository::save)
                .map(beerMapper::beerToBeerDto); //when we save something to the repository we actually get a new object back
        // -the result comming out of save is mapped again as a beerDto
    }

    @Override
    public Mono<BeerDto> updateBeer(Integer beerId, BeerDto beerDto) {
        return beerRepository.findById(beerId)
                .defaultIfEmpty(Beer.builder().build()) //if nothing comes back a beer object with null 'id' is returned from here
                .map(beer -> {
                    beer.setBeerName(beerDto.getBeerName());
                    beer.setBeerStyle(BeerStyleEnum.valueOf(beerDto.getBeerStyle()));
                    beer.setPrice(beerDto.getPrice());
                    beer.setUpc(beerDto.getUpc());
                    return beer;
                })
                .flatMap(updateBeer -> {
                    if (updateBeer.getId() != null){
                        return beerRepository.save(updateBeer);
                    }
                    return Mono.just(updateBeer);
                })
                .map(beerMapper::beerToBeerDto);
    }

    @Cacheable(cacheNames = "beerUpcCache")
    @Override
    public Mono<BeerDto> getByUpc(String upc) {
        return beerRepository.findByUpc(upc).map(beerMapper::beerToBeerDto);
    }

    @Override
    public void deleteBeerById(Integer beerId) {
        beerRepository.deleteById(beerId).subscribe();
    }

    @Override
    public Mono<Void> reactiveDeleteById(Integer beerId) {
        return beerRepository.findById(beerId)
                .switchIfEmpty(Mono.error(new NotFoundException()))//emits a mono with a not found exception in case the find operation does not succeed
                .map(beer -> beer.getId())
                .flatMap(foundId -> beerRepository.deleteById(foundId));
    }
}
