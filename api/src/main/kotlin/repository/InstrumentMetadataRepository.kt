package com.harshsbajwa.stockifai.api.repository

import com.harshsbajwa.stockifai.api.model.InstrumentMetadata
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.stereotype.Repository

@Repository
interface InstrumentMetadataRepository : CassandraRepository<InstrumentMetadata, String>