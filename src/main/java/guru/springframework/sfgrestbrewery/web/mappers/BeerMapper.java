package guru.springframework.sfgrestbrewery.web.mappers;

import guru.springframework.sfgrestbrewery.domain.Beer;
import guru.springframework.sfgrestbrewery.web.model.BeerDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Created by jt on 2019-05-25.
 */
@Mapper(uses = {DateMapper.class})
public interface BeerMapper {

    @Mapping(target = "quantityOnHand", ignore = true) //will exclude that property from the mapping
    BeerDto beerToBeerDto(Beer beer);

    BeerDto beerToBeerDtoWithInventory(Beer beer);

    Beer beerDtoToBeer(BeerDto dto);
}
