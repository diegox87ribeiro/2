package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CarteiraLocalDao {

    // ---------- Ativos ----------

    @Query("SELECT * FROM ativos ORDER BY ticker ASC")
    fun observarAtivos(): Flow<List<Ativo>>

    @Query("SELECT * FROM ativos WHERE UPPER(ticker) = UPPER(:ticker) LIMIT 1")
    fun observarAtivoPorTicker(ticker: String): Flow<Ativo?>

    @Query("SELECT * FROM ativos WHERE UPPER(ticker) = UPPER(:ticker) LIMIT 1")
    suspend fun buscarAtivoPorTicker(ticker: String): Ativo?

    @Query("SELECT * FROM ativos ORDER BY ticker ASC")
    suspend fun listarAtivosUmaVez(): List<Ativo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirAtivo(ativo: Ativo): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirAtivos(ativos: List<Ativo>)

    @Update
    suspend fun atualizarAtivo(ativo: Ativo)

    @Query(
        """
        UPDATE ativos
        SET precoAtual = :precoAtual,
            possuiCotacaoMercado = 1,
            atualizadoEm = :atualizadoEm
        WHERE UPPER(ticker) = UPPER(:ticker)
        """
    )
    suspend fun atualizarCotacaoAtivo(
        ticker: String,
        precoAtual: Double,
        atualizadoEm: Long = System.currentTimeMillis()
    ): Int

    @Query(
        """
        UPDATE ativos
        SET dataComDia = COALESCE(dataComDia, :dataComDia),
            dataExDia = COALESCE(:dataExDia, dataExDia),
            atualizadoEm = :atualizadoEm
        WHERE UPPER(ticker) = UPPER(:ticker)
        """
    )
    suspend fun atualizarDatasDividendoSeAusente(
        ticker: String,
        dataComDia: Int?,
        dataExDia: Int?,
        atualizadoEm: Long = System.currentTimeMillis()
    ): Int

    @Delete
    suspend fun deletarAtivo(ativo: Ativo)

    @Query("DELETE FROM ativos WHERE id = :id")
    suspend fun deletarAtivoPorId(id: Long)

    @Query("DELETE FROM ativos WHERE UPPER(ticker) = UPPER(:ticker)")
    suspend fun deletarAtivoPorTicker(ticker: String)

    @Query("DELETE FROM ativos")
    suspend fun limparAtivos()

    // ---------- Transações ----------

    @Query("SELECT * FROM transacoes ORDER BY criadoEm DESC, id DESC")
    fun observarTransacoes(): Flow<List<Transacao>>

    @Query("SELECT * FROM transacoes WHERE UPPER(ativoTicker) = UPPER(:ticker) ORDER BY criadoEm DESC, id DESC")
    fun observarTransacoesPorTicker(ticker: String): Flow<List<Transacao>>

    @Query("SELECT * FROM transacoes WHERE UPPER(tipo) = UPPER(:tipo) ORDER BY criadoEm DESC, id DESC")
    fun observarTransacoesPorTipo(tipo: String): Flow<List<Transacao>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTransacao(transacao: Transacao): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTransacoes(transacoes: List<Transacao>)

    @Update
    suspend fun atualizarTransacao(transacao: Transacao)

    @Delete
    suspend fun deletarTransacao(transacao: Transacao)

    @Query("DELETE FROM transacoes WHERE id = :id")
    suspend fun deletarTransacaoPorId(id: Long)

    @Query("DELETE FROM transacoes")
    suspend fun limparTransacoes()

    // ---------- Consultas Relacionais & Operações Atômicas ----------

    @Transaction
    @Query("SELECT * FROM ativos ORDER BY ticker ASC")
    fun observarAtivosComTransacoes(): Flow<List<AtivoComTransacoes>>

    @Transaction
    @Query("SELECT * FROM ativos WHERE UPPER(ticker) = UPPER(:ticker) LIMIT 1")
    fun observarAtivoComTransacoes(ticker: String): Flow<AtivoComTransacoes?>

    @Transaction
    suspend fun registrarTransacaoEAtualizarAtivo(
        ativoBase: Ativo,
        transacao: Transacao
    ): Long {
        val cleanTicker = ativoBase.ticker.trim().uppercase()
        val existente = buscarAtivoPorTicker(cleanTicker)
        val tipoNorm = transacao.tipo.trim().uppercase()
        val now = System.currentTimeMillis()

        val ativoAtualizado = when {
            existente == null -> {
                val qtyInicial = when (tipoNorm) {
                    "COMPRA" -> transacao.quantidade.coerceAtLeast(0.0)
                    else -> ativoBase.quantidade.coerceAtLeast(0.0)
                }
                val pmInicial = when (tipoNorm) {
                    "COMPRA" -> transacao.precoUnitario.coerceAtLeast(0.0)
                    else -> ativoBase.precoMedio.coerceAtLeast(0.0)
                }
                ativoBase.copy(
                    id = 0L,
                    ticker = cleanTicker,
                    quantidade = qtyInicial,
                    precoMedio = pmInicial,
                    atualizadoEm = now
                )
            }
            tipoNorm == "COMPRA" && transacao.quantidade > 0.0 -> {
                val novaQty = existente.quantidade + transacao.quantidade
                val novoCustoTotal = (existente.quantidade * existente.precoMedio) +
                    (transacao.quantidade * transacao.precoUnitario) + transacao.taxas
                val novoPm = if (novaQty > 0.0) novoCustoTotal / novaQty else existente.precoMedio
                existente.copy(
                    quantidade = novaQty,
                    precoMedio = novoPm,
                    atualizadoEm = now
                )
            }
            tipoNorm == "VENDA" && transacao.quantidade > 0.0 -> {
                val novaQty = (existente.quantidade - transacao.quantidade).coerceAtLeast(0.0)
                existente.copy(
                    quantidade = novaQty,
                    atualizadoEm = now
                )
            }
            else -> existente.copy(atualizadoEm = now)
        }

        if (existente == null) {
            inserirAtivo(ativoAtualizado)
        } else {
            atualizarAtivo(ativoAtualizado)
        }

        return inserirTransacao(transacao.copy(ativoTicker = cleanTicker))
    }

    @Transaction
    suspend fun sincronizarAtivosSemRemoverTransacoes(novosAtivos: List<Ativo>) {
        val existentesPorTicker = listarAtivosUmaVez().associateBy { it.ticker.trim().uppercase() }
        novosAtivos.forEach { novo ->
            val ticker = novo.ticker.trim().uppercase()
            val existente = existentesPorTicker[ticker]
            if (existente == null) {
                inserirAtivo(novo.copy(id = 0L, ticker = ticker))
            } else {
                atualizarAtivo(
                    existente.copy(
                        nome = novo.nome,
                        classe = novo.classe,
                        setor = novo.setor,
                        quantidade = novo.quantidade,
                        precoMedio = novo.precoMedio,
                        precoAtual = novo.precoAtual,
                        possuiCotacaoMercado = novo.possuiCotacaoMercado,
                        dataComDia = novo.dataComDia,
                        dataExDia = novo.dataExDia ?: existente.dataExDia,
                        origemDados = novo.origemDados,
                        atualizadoEm = novo.atualizadoEm
                    )
                )
            }
        }
    }
}
