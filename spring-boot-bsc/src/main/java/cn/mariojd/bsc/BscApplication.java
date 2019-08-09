package cn.mariojd.bsc;

import cn.hutool.json.JSONUtil;
import cn.mariojd.bsc.util.IpUtil;
import cn.mariojd.bsc.vo.Rank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@RestController
@SpringBootApplication
public class BscApplication {

    public static void main(String[] args) {
        SpringApplication.run(BscApplication.class, args);
    }

    private static final String IP = "ip";

    private static final String COUNT = "count";

    private static final String RANK_CACHE = "rank_cache";

    private static final String ARTICLES_SCORE = "articles_score";

    private AtomicInteger atomicInteger;

    @PostConstruct
    public void initGetCount() {
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        String count = ops.get(COUNT);
        if (StringUtils.isEmpty(count)) {
            count = "0";
            ops.set(COUNT, count);
        }
        atomicInteger = new AtomicInteger(Integer.parseInt(count));
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("rank")
    public List<Rank> rank(HttpServletRequest request) {
        long start = System.currentTimeMillis();
        int count = atomicInteger.addAndGet(1);
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        ops.increment(COUNT);
        String ipAddress = IpUtil.getIpAddr(request);
        if (!StringUtils.isEmpty(ipAddress)) {
            SetOperations<String, String> set = stringRedisTemplate.opsForSet();
            set.add(IP, ipAddress);
        }

        List<Rank> rankList;
        String strRank = ops.get(RANK_CACHE);
        if (StringUtils.isEmpty(strRank)) {
            ZSetOperations<String, String> zSet = stringRedisTemplate.opsForZSet();
            Set<String> ranks = zSet.reverseRange(ARTICLES_SCORE, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (Objects.nonNull(ranks) && ranks.size() > 0) {
                HashOperations<String, String, Object> hash = stringRedisTemplate.opsForHash();
                rankList = ranks.stream().map(article -> {
                    Map<String, Object> map = hash.entries(article);
                    return JSONUtil.toBean(JSONUtil.toJsonStr(map), Rank.class);
                }).collect(Collectors.toList());
                ops.set(RANK_CACHE, JSONUtil.toJsonStr(rankList), 3, TimeUnit.HOURS);
            } else {
                rankList = Collections.emptyList();
            }
        } else {
            rankList = JSONUtil.toList(JSONUtil.parseArray(strRank), Rank.class);
        }
        log.info("===> 第{}次访问，耗时{}ms", count, System.currentTimeMillis() - start);
        return rankList;
    }

}
