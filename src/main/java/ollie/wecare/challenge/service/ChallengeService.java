package ollie.wecare.challenge.service;

import lombok.RequiredArgsConstructor;
import ollie.wecare.challenge.dto.*;
import ollie.wecare.challenge.entity.Challenge;
import ollie.wecare.challenge.entity.ChallengeAttendance;
import ollie.wecare.challenge.repository.ChallengeAttendanceRepository;
import ollie.wecare.challenge.repository.ChallengeRepository;
import ollie.wecare.common.base.BaseException;
import ollie.wecare.user.repository.UserRepository;
import ollie.wecare.user.service.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import static ollie.wecare.common.base.BaseResponseStatus.*;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeAttendanceRepository challengeAttendanceRepository;
    private final UserRepository userRepository;
    private final ChallengeRepository challengeRepository;

    private final AuthService authService;

    /*
     * 참여 중인 챌린지 조회
     * */
    public List<GetChallengesRes> getMyChallenges() throws BaseException {
        Long tmpUserIdx = 1L;
        List<ChallengeAttendance> participationList = challengeAttendanceRepository.findByUser_UserIdx(tmpUserIdx);
        Long participationNum = (long)participationList.size();

        Set<Challenge> challengeSet = new HashSet<>();
        for(ChallengeAttendance ca : participationList)
            challengeSet.add(ca.getChallenge());

        List<Challenge> challengesList = new ArrayList<>(challengeSet);
        return challengesList.stream().map(challenge -> GetChallengesRes.fromChallenge(challenge, participationNum)).toList();
    }

    /*
     * 챌린지 인증
     * */
    //@Transactional
    public void attendChallenge(AttendChallengeReq attendChallengeReq) throws BaseException {
        Challenge challenge = challengeRepository.findById(attendChallengeReq.getChallengeIdx()).orElseThrow(()-> new BaseException(INVALID_CHALLENGE_IDX));
        if(!challenge.getAttendanceCode().equals(attendChallengeReq.getAttendanceCode()))
            throw new BaseException(INVALID_ATTENDANCE_CODE);
        else {
            ChallengeAttendance challengeAttendance = ChallengeAttendance.builder()
                    .user(userRepository.findById(authService.getUserIdx()).orElseThrow(()->new BaseException(INVALID_USER_IDX)))
                    .challenge(challengeRepository.findById(attendChallengeReq.getChallengeIdx()).orElseThrow(()-> new BaseException(INVALID_CHALLENGE_IDX)))
                    .attendanceDate(LocalDateTime.now()).build();
            challengeAttendanceRepository.save(challengeAttendance);
        }
    }

    /*
     * 새로운 챌린지 참여
     * */
    @Transactional
    public void participateChallenge(PostChallengeReq postChallengeReq) throws BaseException {
        ChallengeAttendance challengeAttendance = ChallengeAttendance.builder()
                .user(userRepository.findById(authService.getUserIdx()).orElseThrow(()->new BaseException(INVALID_USER_IDX)))
                .challenge(challengeRepository.findById(postChallengeReq.getChallengeIdx()).orElseThrow(()-> new BaseException(INVALID_CHALLENGE_IDX)))
                .attendanceDate(LocalDateTime.now()).build();
        challengeAttendanceRepository.save(challengeAttendance);
        //TODO : 이미 참여중인 챌린지 처리
    }

    /*
     * 챌린지 검색
     * */
    public List<GetChallengesRes> getChallenges(String searchWord) {
        return challengeRepository.findByNameContaining(searchWord).stream().map(challenge -> GetChallengesRes.fromChallenge(challenge, 0L)).toList();
    }

    /*
     * 챌린지 참여 현황 조회(월별)
     * */
    public List<GetAttendanceRes> getAttendance(Long challengeIdx, Long year, Long month) {
        int y = year.intValue();
        int m = month.intValue();
        LocalDateTime firstDay = LocalDate.of(y, m, 1).atStartOfDay();
        LocalDateTime lastDay = LocalDate.of(y, m, 1).atStartOfDay();
        if(year == 0) {
            firstDay = YearMonth.from(LocalDateTime.now().toLocalDate()).atDay(1).atStartOfDay();
            lastDay = YearMonth.from(LocalDateTime.now().toLocalDate()).atEndOfMonth().atStartOfDay();
        }
        return challengeAttendanceRepository.findByChallenge_ChallengeIdxAndAttendanceDateBetween(challengeIdx, firstDay, lastDay)
                .stream()
                .map(challengeAttendance -> GetAttendanceRes.builder().attendanceDate(challengeAttendance.getAttendanceDate().toLocalDate()).build())
                .collect(Collectors.toList());
    }

    /*
    * 챌린지 배너 조회 (홈화면)
    * */
    public GetChallengeAdsRes getChallengeAds() {

        Challenge mostParticipatedChallenge = challengeRepository.findById(1L).orElseThrow(()-> new BaseException(INVALID_CHALLENGE_IDX));
        Challenge mostAttendancedChallenge = challengeRepository.findById(1L).orElseThrow(()-> new BaseException(INVALID_CHALLENGE_IDX));
        Challenge mostRecentlyStartedChallenge = challengeRepository.findById(1L).orElseThrow(()-> new BaseException(INVALID_CHALLENGE_IDX));

        return GetChallengeAdsRes.builder()
                .mostAttendancedChallenge(mostAttendancedChallenge)
                .mostParticipatedChallenge(mostParticipatedChallenge)
                .mostRecentlyStartedChallenge(mostRecentlyStartedChallenge).build();

    }


}
