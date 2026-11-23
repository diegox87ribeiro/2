package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "ativos",
    indices = [
        Index(value = ["ticker"], unique = true)
    ]
)
data class Ativo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ticker: String,
    val nome: String,
    val classe: String,
    val setor: String = "Geral",
    val quantidade: Double = 0.0,
    val precoMedio: Double = 0.0,
    val precoAtual: Double = 0.0,
    val possuiCotacaoMercado: Boolean = false,
    val dataComDia: Int? = null,
    val dataExDia: Int? = null,
    val ehCiclico: Boolean = true,
    val origemDados: String = "Local",
    val atualizadoEm: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transacoes",
    foreignKeys = [
        ForeignKey(
            entity = Ativo::class,
            parentColumns = ["ticker"],
            childColumns = ["ativoTicker"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ativoTicker"]),
        Index(value = ["dataIso"])
    ]
)
data class Transacao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ativoTicker: String,
    val tipo: String, // COMPRA, VENDA, PROVENTO, APORTE, RESGATE
    val quantidade: Double,
    val precoUnitario: Double,
    val valorTotal: Double,
    val taxas: Double = 0.0,
    val dataIso: String,
    val observacoes: String = "",
    val referenciaExterna: String = "",
    val criadoEm: Long = System.currentTimeMillis()
)

data class AtivoComTransacoes(
    @Embedded val ativo: Ativo,
    @Relation(
        parentColumn = "ticker",
        entityColumn = "ativoTicker"
    )
    val transacoes: List<Transacao>
)
