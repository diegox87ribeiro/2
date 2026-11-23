package com.example.data.repository

import com.example.data.local.Ativo
import com.example.data.local.AtivoComTransacoes
import com.example.data.local.CarteiraLocalDao
import com.example.data.local.Transacao
import kotlinx.coroutines.flow.Flow

class CarteiraLocalRepository(
    private val carteiraLocalDao: CarteiraLocalDao
) {
    val ativosFlow: Flow<List<Ativo>> = carteiraLocalDao.observarAtivos()
    val transacoesFlow: Flow<List<Transacao>> = carteiraLocalDao.observarTransacoes()
    val ativosComTransacoesFlow: Flow<List<AtivoComTransacoes>> = carteiraLocalDao.observarAtivosComTransacoes()

    fun observarAtivoPorTicker(ticker: String): Flow<Ativo?> =
        carteiraLocalDao.observarAtivoPorTicker(ticker.trim().uppercase())

    fun observarTransacoesDoAtivo(ticker: String): Flow<List<Transacao>> =
        carteiraLocalDao.observarTransacoesPorTicker(ticker.trim().uppercase())

    fun observarTransacoesPorTipo(tipo: String): Flow<List<Transacao>> =
        carteiraLocalDao.observarTransacoesPorTipo(tipo.trim().uppercase())

    fun observarAtivoComTransacoes(ticker: String): Flow<AtivoComTransacoes?> =
        carteiraLocalDao.observarAtivoComTransacoes(ticker.trim().uppercase())

    suspend fun salvarAtivo(ativo: Ativo): Long =
        carteiraLocalDao.inserirAtivo(ativo.copy(ticker = ativo.ticker.trim().uppercase()))

    suspend fun atualizarAtivo(ativo: Ativo) =
        carteiraLocalDao.atualizarAtivo(ativo.copy(ticker = ativo.ticker.trim().uppercase()))

    suspend fun atualizarCotacao(ticker: String, precoAtual: Double): Int =
        carteiraLocalDao.atualizarCotacaoAtivo(ticker.trim().uppercase(), precoAtual)

    suspend fun excluirAtivoPorId(id: Long) =
        carteiraLocalDao.deletarAtivoPorId(id)

    suspend fun excluirAtivoPorTicker(ticker: String) =
        carteiraLocalDao.deletarAtivoPorTicker(ticker.trim().uppercase())

    suspend fun registrarTransacao(ativoBase: Ativo, transacao: Transacao): Long =
        carteiraLocalDao.registrarTransacaoEAtualizarAtivo(ativoBase, transacao)

    suspend fun excluirTransacaoPorId(id: Long) =
        carteiraLocalDao.deletarTransacaoPorId(id)

    suspend fun sincronizarAtivos(ativos: List<Ativo>) =
        carteiraLocalDao.sincronizarAtivosSemRemoverTransacoes(ativos)

    suspend fun limparCarteiraLocal() {
        carteiraLocalDao.limparTransacoes()
        carteiraLocalDao.limparAtivos()
    }
}
