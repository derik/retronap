package com.deriklima.retronap.user;

import com.deriklima.retronap.model.UserInfo;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface UserInfoRepository extends JpaRepository<UserInfo, UUID> {}
