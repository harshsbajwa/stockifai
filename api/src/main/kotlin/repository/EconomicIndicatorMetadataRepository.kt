package com.harshsbajwa.stockifai.api.repository

import com.harshsbajwa.stockifai.api.model.EconomicIndicatorMetadata
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.stereotype.Repository

@Repository
interface EconomicIndicatorMetadataRepository : CassandraRepository<EconomicIndicatorMetadata, String>