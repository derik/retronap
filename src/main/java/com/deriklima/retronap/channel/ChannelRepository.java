package com.deriklima.retronap.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface ChannelRepository extends JpaRepository<Channel, String> {}
