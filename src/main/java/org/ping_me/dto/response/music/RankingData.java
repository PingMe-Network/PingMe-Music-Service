package org.ping_me.dto.response.music;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ping_me.dto.response.music.misc.TopSongPlayCounterDto;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingData {
    private List<TopSongPlayCounterDto> today;
    private List<TopSongPlayCounterDto> week;
    private List<TopSongPlayCounterDto> month;
}

